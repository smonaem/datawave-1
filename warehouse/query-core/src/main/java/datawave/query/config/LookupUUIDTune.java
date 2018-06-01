package datawave.query.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import datawave.query.Constants;
import datawave.query.planner.DefaultQueryPlanner;
import datawave.query.planner.QueryPlanner;
import datawave.query.planner.SeekingQueryPlanner;
import datawave.query.tables.ShardQueryLogic;
import datawave.query.tld.CreateTLDUidsIterator;
import datawave.query.tld.TLDQueryIterator;
import datawave.webservice.query.configuration.GenericQueryConfiguration;
import datawave.webservice.query.logic.BaseQueryLogic;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;

public class LookupUUIDTune implements Profile {
    
    protected boolean bypassAccumulo = false;
    protected boolean enableCaching = false;
    protected boolean disableComplexFunctions = false;
    protected boolean reduceResponse = false;
    protected boolean enablePreload = false;
    protected boolean speculativeScanning = false;
    protected int maxFieldHitsBeforeSeek = -1;
    protected int maxKeysBeforeSeek = -1;
    protected String queryIteratorClass = TLDQueryIterator.class.getCanonicalName();
    protected int maxShardsPerDayThreshold = -1;
    protected int pageByteTrigger = -1;
    protected int maxPageSize = -1;
    protected Map<String,List<String>> primaryToSecondaryFieldMap = Collections.emptyMap();
    protected boolean trackSizes = true;
    protected boolean reduceFields = false;
    protected int reduceFieldCount = -1;
    protected boolean reduceFieldsPreQueryEvaluation = false;
    protected String limitFieldsField = null;
    
    @Override
    public void configure(BaseQueryLogic<Entry<Key,Value>> logic) {
        if (logic instanceof ShardQueryLogic) {
            ShardQueryLogic rsq = ShardQueryLogic.class.cast(logic);
            rsq.setBypassAccumulo(bypassAccumulo);
            rsq.setSpeculativeScanning(speculativeScanning);
            rsq.setCacheModel(enableCaching);
            rsq.setPrimaryToSecondaryFieldMap(primaryToSecondaryFieldMap);
            if (reduceResponse) {
                rsq.setCreateUidsIteratorClass(CreateTLDUidsIterator.class);
                
                // setup SeekingQueryPlanner in case the queryIterator requires it
                SeekingQueryPlanner planner = new SeekingQueryPlanner();
                planner.setMaxFieldHitsBeforeSeek(maxFieldHitsBeforeSeek);
                planner.setMaxKeysBeforeSeek(maxKeysBeforeSeek);
                rsq.setQueryPlanner(planner);
                
                if (maxPageSize != -1) {
                    rsq.setMaxPageSize(maxPageSize);
                }
                
                if (pageByteTrigger != -1) {
                    rsq.setPageByteTrigger(pageByteTrigger);
                }
            }
        }
        
    }
    
    @Override
    public void configure(QueryPlanner planner) {
        if (planner instanceof DefaultQueryPlanner) {
            DefaultQueryPlanner dqp = DefaultQueryPlanner.class.cast(planner);
            dqp.setCacheDataTypes(enableCaching);
            dqp.setCondenseUidsInRangeStream(false);
            if (disableComplexFunctions) {
                dqp.setDisableAnyFieldLookup(true);
                dqp.setDisableBoundedLookup(true);
                dqp.setDisableCompositeFields(true);
                dqp.setDisableExpandIndexFunction(true);
                dqp.setDisableRangeCoalescing(true);
                dqp.setDisableTestNonExistentFields(true);
                if (reduceResponse)
                    try {
                        Class iteratorClass = Class.forName(this.queryIteratorClass);
                        dqp.setQueryIteratorClass(iteratorClass);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException("Cannot Instantiate queryIteratorClass: " + this.queryIteratorClass, e);
                    }
            }
            if (enablePreload) {
                dqp.setPreloadOptions(true);
            }
        }
        
    }
    
    @Override
    public void configure(GenericQueryConfiguration configuration) {
        if (configuration instanceof ShardQueryConfiguration) {
            ShardQueryConfiguration rsqc = ShardQueryConfiguration.class.cast(configuration);
            rsqc.setTldQuery(reduceResponse);
            rsqc.setBypassAccumulo(bypassAccumulo);
            rsqc.setSerializeQueryIterator(true);
            rsqc.setMaxEvaluationPipelines(1);
            rsqc.setMaxPipelineCachedResults(1);
            if (maxShardsPerDayThreshold != -1) {
                rsqc.setShardsPerDayThreshold(maxShardsPerDayThreshold);
            }
            // we need this since we've finished the deep copy already
            rsqc.setSpeculativeScanning(speculativeScanning);
            rsqc.setTrackSizes(trackSizes);
            
            if (reduceResponse) {
                if (reduceFields && reduceFieldCount != -1) {
                    Set<String> fieldLimits = new HashSet<>(1);
                    fieldLimits.add(Constants.ANY_FIELD + "=" + reduceFieldCount);
                    rsqc.setLimitFields(fieldLimits);
                    rsqc.setLimitFieldsPreQueryEvaluation(reduceFieldsPreQueryEvaluation);
                    rsqc.setLimitFieldsField(limitFieldsField);
                }
            }
        }
    }
    
    public boolean getSpeculativeScanning() {
        return speculativeScanning;
    }
    
    public void setSpeculativeScanning(boolean speculativeScanning) {
        this.speculativeScanning = speculativeScanning;
    }
    
    public void setBypassAccumulo(boolean bypassAccumulo) {
        this.bypassAccumulo = bypassAccumulo;
    }
    
    public boolean getBypassAccumulo() {
        return bypassAccumulo;
    }
    
    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
    }
    
    public boolean getEnableCaching() {
        return enableCaching;
    }
    
    public void setEnablePreload(boolean enablePreload) {
        this.enablePreload = enablePreload;
    }
    
    public boolean getEnablePreload() {
        return enablePreload;
    }
    
    public boolean getDisableComplexFunctions() {
        return disableComplexFunctions;
    }
    
    public void setDisableComplexFunctions(boolean disableComplexFunctions) {
        this.disableComplexFunctions = disableComplexFunctions;
    }
    
    public void setReduceResponse(boolean forceTld) {
        this.reduceResponse = forceTld;
    }
    
    public boolean getReduceResponse() {
        return reduceResponse;
    }
    
    public void setMaxFieldHitsBeforeSeek(int maxFieldHitsBeforeSeek) {
        this.maxFieldHitsBeforeSeek = maxFieldHitsBeforeSeek;
    }
    
    public int getMaxFieldHitsBeforeSeek() {
        return maxFieldHitsBeforeSeek;
    }
    
    public void setMaxKeysBeforeSeek(int maxKeysBeforeSeek) {
        this.maxKeysBeforeSeek = maxKeysBeforeSeek;
    }
    
    public int getMaxKeysBeforeSeek() {
        return maxKeysBeforeSeek;
    }
    
    public void setQueryIteratorClass(String queryIteratorClass) {
        this.queryIteratorClass = queryIteratorClass;
    }
    
    public String getQueryIteratorClass() {
        return queryIteratorClass;
    }
    
    public int getMaxShardsPerDayThreshold() {
        return maxShardsPerDayThreshold;
    }
    
    public void setMaxShardsPerDayThreshold(int maxShardsPerDayThreshold) {
        this.maxShardsPerDayThreshold = maxShardsPerDayThreshold;
    }
    
    public int getPageByteTrigger() {
        return pageByteTrigger;
    }
    
    public void setPageByteTrigger(int pageByteTrigger) {
        this.pageByteTrigger = pageByteTrigger;
    }
    
    public int getMaxPageSize() {
        return maxPageSize;
    }
    
    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
    
    public void setPrimaryToSecondaryFieldMap(Map<String,List<String>> primaryToSecondaryFieldMap) {
        this.primaryToSecondaryFieldMap = primaryToSecondaryFieldMap;
    }
    
    public Map<String,List<String>> getPrimaryToSecondaryFieldMap() {
        return primaryToSecondaryFieldMap;
    }
    
    public boolean isTrackSizes() {
        return trackSizes;
    }
    
    public void setTrackSizes(boolean trackSizes) {
        this.trackSizes = trackSizes;
    }
    
    public boolean isReduceFields() {
        return reduceFields;
    }
    
    public void setReduceFields(boolean reduceFields) {
        this.reduceFields = reduceFields;
    }
    
    public int getReduceFieldCount() {
        return reduceFieldCount;
    }
    
    public void setReduceFieldCount(int reduceFieldCount) {
        this.reduceFieldCount = reduceFieldCount;
    }
    
    public boolean isReduceFieldsPreQueryEvaluation() {
        return reduceFieldsPreQueryEvaluation;
    }
    
    public void setReduceFieldsPreQueryEvaluation(boolean reduceFieldsPreQueryEvaluation) {
        this.reduceFieldsPreQueryEvaluation = reduceFieldsPreQueryEvaluation;
    }
    
    public void setLimitFieldsField(String limitFieldsField) {
        this.limitFieldsField = limitFieldsField;
    }
    
    public String getLimitFieldsField() {
        return limitFieldsField;
    }
}
