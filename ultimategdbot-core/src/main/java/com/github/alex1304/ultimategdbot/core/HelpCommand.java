package com.github.alex1304.ultimategdbot.core;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotation.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotation.CommandSpec;

import reactor.core.publisher.Mono;

@CommandSpec(aliases={ "help", "manual" })
class HelpCommand  {
	
	@CommandAction
	public Mono<Void> execute(Context ctx) {
		return Mono.empty();
	}
}
