package com.jd.journalq.common.hbase;

import com.jd.journalq.toolkit.lang.Close;
import com.jd.journalq.toolkit.lang.LifeCycle;
import com.jd.journalq.toolkit.lang.Pair;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * HBASE客户端
 * <p>
 * Created by chengzhiliang on 2018/12/6.
 */
public class HBaseClient implements LifeCycle {
    private static final Logger logger = LoggerFactory.getLogger(HBaseClient.class);

    public Configuration config = HBaseConfiguration.create();

    public Connection conn = null;

    private final String nameSpace = "jmq4";

    private String hBaseConfigPath = "hBase-client-config.xml";

    private boolean isStart = false;

    public HBaseClient() {
    }

    public HBaseClient(String hBaseConfigPath) {
        this.hBaseConfigPath = hBaseConfigPath;
    }

    @Override
    public void start() throws Exception{
        config.addResource(hBaseConfigPath);
        // 建立连接
        try {
            conn = ConnectionFactory.createConnection(config);
            isStart = true;
            logger.info("HBaseClient is started.");
        } catch (Exception e) {
            isStart = false;
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean isStarted() {
        return isStart;
    }

    @Override
    public void stop() {
        Close.close(conn);
    }

    public void put(String tableName, byte[] cf, byte[] col, List<Pair<byte[], byte[]>> pairList) throws IOException {
        Table table = conn.getTable(TableName.valueOf(nameSpace, tableName));

        List<Put> list = new LinkedList<>();
        for (Pair<byte[], byte[]> pair : pairList) {
            Put put = new Put(pair.getKey());
            put.addColumn(cf, col, pair.getValue());
            list.add(put);
        }

        table.put(list);
    }

    public void put(String tableName, byte[] cf, byte[] col, byte[] rowKey, byte[] val) throws IOException {
        Table table = conn.getTable(TableName.valueOf(nameSpace, tableName));
        Put put = new Put(rowKey);
        put.addColumn(cf, col, val);
        table.put(put);
    }


    public List<Pair<byte[], byte[]>> scan(ScanParameters args) throws IOException {
        List<Pair<byte[], byte[]>> list = new LinkedList<>();
        Table table = conn.getTable(TableName.valueOf(nameSpace, args.getTableName()));

        Scan scan = new Scan().withStartRow(args.getStartRowKey(), false).setLimit(args.getRowCount());
        if (args.getStopRowKey() != null) {
            scan.withStopRow(args.getStopRowKey(), true);
        }

        ResultScanner scanner = table.getScanner(scan);
        scanner.forEach(result -> list.add(new Pair<>(result.getRow(), result.getValue(args.getCf(), args.getCol()))));

        return list;
    }

    public byte[] get(String tableName, byte[] cf, byte[] col, byte[] rowKey) throws IOException {
        Table table = conn.getTable(TableName.valueOf(nameSpace, tableName));
        Get get = new Get(rowKey);
        Result result = table.get(get);
        return result.getValue(cf, col);
    }

    public Pair<byte[], byte[]> getKV(String tableName, byte[] cf, byte[] col, byte[] rowKey) throws IOException {
        Table table = conn.getTable(TableName.valueOf(nameSpace, tableName));

        Get get = new Get(rowKey);
        Result result = table.get(get);
        // result.getValue(cf, col);
        return new Pair<>(result.getRow(), result.getValue(cf, col));
    }

    public boolean checkAndPut(String tableName, byte[] cf, byte[] col, byte[] rowKey, byte[] expect, byte[] value) throws IOException {
        Table table = conn.getTable(TableName.valueOf(nameSpace, tableName));

        Put put = new Put(rowKey);
        put.addColumn(cf, col, value);

        return table.checkAndPut(rowKey, cf, col, expect, put);
    }

    /**
     * 查询参数对象
     */
    public static class ScanParameters {
        private String tableName;
        private byte[] cf;
        private byte[] col;
        private byte[] startRowKey;
        private byte[] stopRowKey;
        private int rowCount;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public byte[] getCf() {
            return cf;
        }

        public void setCf(byte[] cf) {
            this.cf = cf;
        }

        public byte[] getCol() {
            return col;
        }

        public void setCol(byte[] col) {
            this.col = col;
        }

        public byte[] getStartRowKey() {
            return startRowKey;
        }

        public void setStartRowKey(byte[] startRowKey) {
            this.startRowKey = startRowKey;
        }

        public byte[] getStopRowKey() {
            return stopRowKey;
        }

        public void setStopRowKey(byte[] stopRowKey) {
            this.stopRowKey = stopRowKey;
        }

        public int getRowCount() {
            return rowCount;
        }

        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }
    }



}