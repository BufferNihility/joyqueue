package io.openmessaging.jmq.extension;

import com.jd.journalq.client.internal.metadata.domain.PartitionMetadata;
import com.jd.journalq.common.network.domain.BrokerNode;
import io.openmessaging.extension.QueueMetaData;

/**
 * PartitionAdapter
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/3/1
 */
public class PartitionAdapter implements QueueMetaData.Partition {

    private PartitionMetadata partitionMetadata;

    public PartitionAdapter(PartitionMetadata partitionMetadata) {
        this.partitionMetadata = partitionMetadata;
    }

    @Override
    public int partitionId() {
        return partitionMetadata.getId();
    }

    @Override
    public String partitonHost() {
        BrokerNode leader = partitionMetadata.getLeader();
        if (leader == null) {
            return null;
        }
        return leader.getHost() + ":" + leader.getPort();
    }
}