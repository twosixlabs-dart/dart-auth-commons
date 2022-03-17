CREATE KEYSPACE IF NOT EXISTS dart WITH REPLICATION = { 'class' : 'org.apache.cassandra.locator.SimpleStrategy', 'replication_factor': '1' };

CREATE TABLE dart.tenants_docs
(
    tenant_id text,
    doc_id    text,
    PRIMARY KEY (tenant_id, doc_id)
);

CREATE INDEX IF NOT EXISTS ON dart.tenants_docs (doc_id);
