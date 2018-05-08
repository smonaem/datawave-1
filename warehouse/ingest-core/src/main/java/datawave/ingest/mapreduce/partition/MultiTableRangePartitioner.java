package datawave.ingest.mapreduce.partition;

import datawave.ingest.mapreduce.job.*;
import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Map;

/**
 * Range partitioner that uses a split file with the format: {@code tableName<tab>splitPoint<tab>tabletLocation}
 * 
 */
public class MultiTableRangePartitioner extends Partitioner<BulkIngestKey,Value> implements DelegatePartitioner {
    private static final String PREFIX = MultiTableRangePartitioner.class.getName();
    public static final String PARTITION_STATS = PREFIX + ".partitionStats";
    
    private static final Logger log = Logger.getLogger(MultiTableRangePartitioner.class);
    static TaskInputOutputContext<?,?,?,?> context = null;
    private static boolean collectStats = false;
    
    protected volatile boolean cacheFilesRead = false;
    private Text holder = new Text();
    protected ThreadLocal<Map<String,Text[]>> splitsByTable = new ThreadLocal<Map<String,Text[]>>();
    protected ThreadLocal<Map<String,Map<Text,String>>> splitToLocationMap = new ThreadLocal<>();
    private DecimalFormat formatter = new DecimalFormat("000");
    private Configuration conf;
    private PartitionLimiter partitionLimiter;
    protected Object semaphore = new Object();
    
    private void readCacheFilesIfNecessary() {
        if (splitsByTable.get() != null && splitToLocationMap.get() != null) {
            return;
        }
        
        synchronized (semaphore) {
            if (splitsByTable.get() != null && splitToLocationMap.get() != null) {
                return;
            }
            
            Path[] localCacheFiles;
            
            try {
                // Moved the deprecation call from NonShardedSplitsFile to simplify testing
                // We need a replacement that isn't deprecated, but context.getCacheFiles() returns paths that are not local
                // No Hadoop documentation seems to indicate what is the correct replacement for this method
                localCacheFiles = context.getLocalCacheFiles();
            } catch (IOException e) {
                log.error("Failed to get localCacheFiles from context", e);
                throw new RuntimeException("Failed to get localCacheFiles from context", e);
            }
            
            try {
                NonShardedSplitsFile.Reader reader = new NonShardedSplitsFile.Reader(context.getConfiguration(), localCacheFiles, getSplitsFileType());
                splitToLocationMap.set(reader.getSplitsAndLocationsByTable());
                splitsByTable.set(reader.getSplitsByTable());
                if (splitsByTable.get().isEmpty() && splitToLocationMap.get().isEmpty()) {
                    log.error("Non-sharded splits by table cannot be empty.  If this is a development system, please create at least one split in one of the non-sharded tables (see bin/ingest/seed_index_splits.sh).");
                    throw new IOException("splits by table cannot be empty");
                }
            } catch (IOException e) {
                log.error("Failed to read splits in MultiTableRangePartitioner: cache files: " + Arrays.toString(localCacheFiles), e);
                throw new RuntimeException("Failed to read splits in MultiTableRangePartitioner, fatal error. cache files: " + Arrays.toString(localCacheFiles));
                
            }
            cacheFilesRead = true;
        }
    }
    
    @Override
    public int getPartition(BulkIngestKey key, Value value, int numPartitions) {
        readCacheFilesIfNecessary();
        
        String tableName = key.getTableName().toString();
        Text[] cutPointArray = splitsByTable.get().get(tableName);
        
        if (null == cutPointArray)
            return (tableName.hashCode() & Integer.MAX_VALUE) % numPartitions;
        key.getKey().getRow(holder);
        int index = Arrays.binarySearch(cutPointArray, holder);
        index = calculateIndex(index, numPartitions, tableName, cutPointArray.length);
        
        index = partitionLimiter.limit(numPartitions, index);
        
        TaskInputOutputContext<?,?,?,?> c = context;
        if (c != null && collectStats) {
            c.getCounter("Partitions: " + key.getTableName(), "part." + formatter.format(index)).increment(1);
        }
        
        return index;
    }
    
    protected int calculateIndex(int index, int numPartitions, String tableName, int cutPointArrayLength) {
        
        return index < 0 ? (index + 1) * -1 : index;
        
    }
    
    public static void setContext(TaskInputOutputContext<?,?,?,?> context) {
        MultiTableRangePartitioner.context = context;
        collectStats = (context != null) && context.getConfiguration().getBoolean(PARTITION_STATS, false);
    }
    
    @Override
    public void initializeJob(Job job) {
        try {
            Configuration conf = job.getConfiguration();
            // only create the splits file if we haven't already created it, possibly for another table
            if (null == NonShardedSplitsFile.findSplitsFile(job.getConfiguration(), job.getLocalCacheFiles(), getSplitsFileType())) {
                URI splitsFileUri = createTheSplitsFile(conf);
                job.addCacheFile(splitsFileUri);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize partitioner for job", e);
        }
    }
    
    private URI createTheSplitsFile(Configuration conf) throws IOException, URISyntaxException, TableNotFoundException, TableExistsException {
        int reduceTasks = conf.getInt("splits.num.reduce", 1);
        String[] tableNames = conf.get("job.table.names").split(",");
        Path workDirPath = new Path(conf.get("ingest.work.dir.qualified"));
        FileSystem outputFs = FileSystem.get(new URI(conf.get("output.fs.uri")), conf);
        NonShardedSplitsFile.Writer writer = new NonShardedSplitsFile.Writer(conf, reduceTasks, workDirPath, outputFs, tableNames, getSplitsFileType());
        writer.createFile(getSplitsFileType());
        return writer.getUri();
    }
    
    protected SplitsFileType getSplitsFileType() {
        return SplitsFileType.TRIMMEDBYNUMBER;
    }
    
    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        partitionLimiter = new PartitionLimiter(conf);
        if (partitionLimiter.getNumPartitions() == 0) {
            partitionLimiter.setMaxPartitions(Integer.MAX_VALUE);
        }
    }
    
    // There could be multiple instances of this partitioner, for different tables.
    // Each may have a different setting
    @Override
    public void configureWithPrefix(String prefix) {
        int originalMax = partitionLimiter.getNumPartitions();
        partitionLimiter.configureWithPrefix(prefix);
        if (0 == partitionLimiter.getNumPartitions()) {
            partitionLimiter.setMaxPartitions(originalMax);
        }
    }
    
    @Override
    public int getNumPartitions() {
        return partitionLimiter.getNumPartitions();
    }
    
    @Override
    public Configuration getConf() {
        return this.conf;
    }
}
