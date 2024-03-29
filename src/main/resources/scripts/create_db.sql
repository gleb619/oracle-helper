ALTER SESSION SET "_ORACLE_SCRIPT"=true;
CREATE USER ${DATABASE_NAME} IDENTIFIED BY knabi;
CREATE TABLESPACE ${DATABASE_NAME}_INDEX DATAFILE '${DATABASE_NAME}_index.dbf' SIZE 1M;
ALTER DATABASE DATAFILE '${DATABASE_NAME}_index.dbf' AUTOEXTEND ON MAXSIZE UNLIMITED;
ALTER USER ${DATABASE_NAME} QUOTA UNLIMITED ON ${DATABASE_NAME}_INDEX;
GRANT CONNECT, UNLIMITED TABLESPACE, CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE ANY TRIGGER, CREATE PROCEDURE TO ${DATABASE_NAME};