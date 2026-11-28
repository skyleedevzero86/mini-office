package com.sleekydz86.contentmeta.catalog.infrastructure;

import com.sleekydz86.contentmeta.global.procedure.ProcedureCall;

public class ExternalIdCudCall extends ProcedureCall {

    private String operation;
    private String worker;
    private String workip;
    private String entityType;
    private long entityId;
    private String provider;
    private String externalId;

    public ExternalIdCudCall(String operation, String worker, String workip, String entityType, long entityId, String provider, String externalId) {
        super("sp_external_id_cud");
        this.operation = operation;
        this.worker = worker;
        this.workip = workip;
        this.entityType = entityType;
        this.entityId = entityId;
        this.provider = provider;
        this.externalId = externalId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public String getWorkip() {
        return workip;
    }

    public void setWorkip(String workip) {
        this.workip = workip;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

}
