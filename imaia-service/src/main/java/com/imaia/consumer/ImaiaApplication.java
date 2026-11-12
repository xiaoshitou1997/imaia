package com.imaia.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Generic consumer of the agent-wechat daemon's event stream. It connects to the daemon
 * over HTTP, replays events it has not handled yet, dispatches them to registered
 * {@link com.imaia.consumer.adapter.wechat.DaemonEventHandler} beans and records progress
 * through a local cursor plus the daemon's consumer acknowledgement.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImaiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImaiaApplication.class, args);
    }
}
