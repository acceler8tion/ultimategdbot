package com.github.alex1304.ultimategdbot.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import com.github.alex1304.ultimategdbot.api.Database;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class DatabaseImpl implements Database {
	
	private SessionFactory sessionFactory = null;
	private final Set<String> resourceNames;
	
	public DatabaseImpl() {
		this.resourceNames = new HashSet<>();
	}

	@Override
	public void configure() {
		var config = new Configuration();
		for (var resource : resourceNames) {
			config.addResource(resource);
		}
		if (sessionFactory != null) {
			sessionFactory.close();
		}
		sessionFactory = config.buildSessionFactory();
	}

	public void addAllMappingResources(Set<String> resourceNames) {
		this.resourceNames.addAll(Objects.requireNonNull(resourceNames));
	}

	@Override
	public <T, K extends Serializable> Mono<T> findByID(Class<T> entityClass, K key) {
		return Mono.fromCallable(() -> {
			try (var s = newSession()) {
				return s.get(entityClass, key);
			}
		}).subscribeOn(Schedulers.elastic());
	}

	@Override
	public <T, K extends Serializable> Mono<T> findByIDOrCreate(Class<T> entityClass, K key, BiConsumer<? super T, K> keySetter) {
		return findByID(entityClass, key).switchIfEmpty(Mono.fromCallable(() -> {
			T result = entityClass.getConstructor().newInstance();
			keySetter.accept(result, key);
			save(result).subscribe();
			return result;
		}).subscribeOn(Schedulers.elastic()));
	}

	@Override
	public <T> Flux<T> query(Class<T> entityClass, String query, Object... params) {
		return Mono.fromCallable(() -> {
			var list = new ArrayList<T>();
			synchronized (sessionFactory) {
				try (var s = newSession()) {
					var q = s.createQuery(query, entityClass);
					for (int i = 0; i < params.length; i++) {
						q.setParameter(i, params[i]);
					}
					list.addAll(q.getResultList());
				}
			}
			return list;
		}).subscribeOn(Schedulers.elastic()).flatMapMany(Flux::fromIterable);
	}

	@Override
	public Mono<Void> save(Object obj) {
		return performEmptyTransaction(session -> session.saveOrUpdate(obj));
	}

	@Override
	public Mono<Void> delete(Object obj) {
		return performEmptyTransaction(session -> session.delete(obj));
	}

	@Override
	public Mono<Void> performEmptyTransaction(Consumer<Session> txConsumer) {
		return Mono.<Void>fromCallable(() -> {
			synchronized (sessionFactory) {
				Transaction tx = null;
				try (var s = newSession()) {
					tx = s.beginTransaction();
					txConsumer.accept(s);
					tx.commit();
				} catch (RuntimeException e) {
					if (tx != null)
						tx.rollback();
					throw e;
				}
				return null;
			}
		}).subscribeOn(Schedulers.elastic()).onErrorMap(e -> new RuntimeException("Error while performing database transaction", e));
	}
	
	@Override
	public <V> Mono<V> performTransaction(Function<Session, V> txFunction) {
		return Mono.fromCallable(() -> {
			synchronized (sessionFactory) {
				V returnVal;
				Transaction tx = null;
				try (var s = newSession()) {
					tx = s.beginTransaction();
					returnVal = txFunction.apply(s);
					tx.commit();
				} catch (RuntimeException e) {
					if (tx != null)
						tx.rollback();
					throw e;
				}
				return returnVal;
			}
		}).subscribeOn(Schedulers.elastic()).onErrorMap(e -> new RuntimeException("Error while performing database transaction", e));
	}

	private Session newSession() {
		if (sessionFactory == null || sessionFactory.isClosed())
			throw new IllegalStateException("Database not configured");

		return sessionFactory.openSession();
	}
}
