package com.darksci.kafka.webview.ui.manager.kafka.filter;

import com.darksci.kafka.webview.ui.manager.kafka.config.RecordFilterDefinition;
import com.darksci.kafka.webview.ui.plugin.filter.RecordFilter;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * RecordFilter Interceptor.  Used to apply 'server-side' filtering.
 */
public class RecordFilterInterceptor implements ConsumerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(RecordFilterInterceptor.class);
    public static final String CONFIG_KEY = "RecordFilterInterceptor.recordFilterDefinitions";

    private final List<RecordFilterDefinition> recordFilterDefinitions = new ArrayList<>();

    @Override
    public ConsumerRecords onConsume(final ConsumerRecords records) {

        final Map<TopicPartition, List<ConsumerRecord>> filteredRecords = new HashMap<>();

        // Iterate thru records
        final Iterator<ConsumerRecord> recordIterator = records.iterator();
        while (recordIterator.hasNext()) {
            final ConsumerRecord record = recordIterator.next();

            boolean result = true;

            // Iterate through filters
            for (final RecordFilterDefinition recordFilterDefinition : recordFilterDefinitions) {
                // Pass through filter
                result = recordFilterDefinition.getRecordFilter().filter(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    record.value()
                );

                // If we return false
                if (!result) {
                    // break out of loop
                    break;
                }
            }

            // If filter return true
            if (result) {
                // Include it in the results
                final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
                filteredRecords.putIfAbsent(topicPartition, new ArrayList<>());
                filteredRecords.get(topicPartition).add(record);
            }
        }

        // return filtered results
        return new ConsumerRecords(filteredRecords);
    }

    @Override
    public void close() {
        // Call close on each filter.
        for (final RecordFilterDefinition recordFilterDefinition : recordFilterDefinitions) {
            recordFilterDefinition.getRecordFilter().close();
        }
    }

    @Override
    public void onCommit(final Map offsets) {
        // Nothing!
    }

    @Override
    public void configure(final Map<String, ?> consumerConfigs) {
        // Make immutable copy.
        final Map<String, ?> immutableConsumerConfigs = Collections.unmodifiableMap(consumerConfigs);

        // Grab definitions out of config
        final Iterable<RecordFilterDefinition> filterDefinitionsCfg = (Iterable<RecordFilterDefinition>) consumerConfigs.get(CONFIG_KEY);

        // Loop over
        for (final RecordFilterDefinition recordFilterDefinition : filterDefinitionsCfg) {
            try {
                // Grab filter and options
                final RecordFilter recordFilter = recordFilterDefinition.getRecordFilter();
                final Map<String, String> filterOptions = recordFilterDefinition.getOptions();

                // Configure it
                recordFilter.configure(immutableConsumerConfigs, filterOptions);

                // Add to list
                recordFilterDefinitions.add(recordFilterDefinition);
            } catch (final Exception exception) {
                logger.error(exception.getMessage(), exception);
            }
        }
    }
}