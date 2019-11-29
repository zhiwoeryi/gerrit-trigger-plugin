/*
 *  The MIT License
 *
 *  Copyright 2014 rinrinne a.k.a. rin_ne All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.impls;

import com.sonyericsson.hudson.plugins.gerrit.trigger.api.GerritTriggerApi;
import com.sonymobile.tools.gerrit.gerritevents.Handler;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import hudson.Extension;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.MessageQueueListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A listener for gerrit events as RabbitMQ message.
 *
 * @author rinrinne a.k.a. rin_ne (rinrin.ne@gmail.com)
 */
public class KafkaMessageListenerImpl {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageListenerImpl.class);

    private static final String PLUGIN_NAME = "Gerrit Trigger";
    private static final String PLUGIN_APPID = "gerrit";
    private static final String JSON_CONTENTTYPE = "application/json";

    private static final String GERRIT_NAME      = "gerrit-name";
    private static final String GERRIT_HOST      = "gerrit-host";
    private static final String GERRIT_SCHEME    = "gerrit-scheme";
    private static final String GERRIT_PORT      = "gerrit-port";
    private static final String GERRIT_FRONT_URL = "gerrit-front-url";
    private static final String GERRIT_VERSION   = "gerrit-version";

    private GerritTriggerApi api = null;
    private Set<String> queueNames = new CopyOnWriteArraySet<String>();

    public String getName() {
        return PLUGIN_NAME;
    }

    public String getAppId() {
        return PLUGIN_APPID;
    }

    public void onBind(String queueName) {
        logger.info("Binded to " + queueName);
        queueNames.add(queueName);
    }

    public void onUnbind(String queueName) {
        logger.info("Unbinded from " + queueName);
        queueNames.remove(queueName);
    }

    private void initKafka() {
        Properties props = new Properties();

        props.put("bootstrap.servers", "");
        props.put("group.id", "1");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        Consumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("", ""));
        consumer.seekToBeginning(new ArrayList<>());

        // ===== 拿到所有的topic ===== //
        Map<String, List<PartitionInfo>> listTopics = consumer.listTopics();
        Set<Map.Entry<String, List<PartitionInfo>>> entries = listTopics.entrySet();

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(1000 * 60);
            for(ConsumerRecord<String, String> record : records) {
                System.out.println("[fetched from partition " + record.partition() + ", offset: " + record.offset() + ", message: " + record.value() + "]");
            }
        }
    }

    public void onReceive(Map<String, Object> headers, byte[] body) {
        logger.debug("Message received.");
        Provider provider = new Provider();
        if (headers != null) {
            if (headers.containsKey(GERRIT_NAME)) {
                provider.setName(headers.get(GERRIT_NAME).toString());
            }
            if (headers.containsKey(GERRIT_HOST)) {
                provider.setHost(headers.get(GERRIT_HOST).toString());
            }
            if (headers.containsKey(GERRIT_SCHEME)) {
                provider.setScheme(headers.get(GERRIT_SCHEME).toString());
            }
            if (headers.containsKey(GERRIT_PORT)) {
                provider.setPort(headers.get(GERRIT_PORT).toString());
            }
            if (headers.containsKey(GERRIT_FRONT_URL)) {
                provider.setUrl(headers.get(GERRIT_FRONT_URL).toString());
            }
            if (headers.containsKey(GERRIT_VERSION)) {
                provider.setVersion(headers.get(GERRIT_VERSION).toString());
            }
        }

        if (api == null) {
            api = new GerritTriggerApi();
        }
        try {
            Handler handler = api.getHandler();
            handler.post(new String(body, "UTF-8"), provider);
        } catch (Exception ex) {
            logger.warn("No handler for Gerrit Trigger. Message would be lost.");
        }
    }
}
