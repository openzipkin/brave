package brave.druid;

/**
 * @author tianjunwei
 * @date 2019/3/19 17:46
 */

import brave.Span;
import brave.propagation.ThreadLocalSpan;
import com.alibaba.druid.filter.FilterEventAdapter;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;

import java.net.URI;
import java.sql.SQLException;

/**
 * A DB statement interceptor that will report to Zipkin how long each statement takes.
 */
public class TracingStatementFilter extends FilterEventAdapter {


    private String zipkinServiceName;

    public TracingStatementFilter(String zipkinServiceName){
        this.zipkinServiceName = zipkinServiceName;
    }

    public TracingStatementFilter(){

    }


    @Override
    protected void statementExecuteUpdateBefore(StatementProxy statement, String sql) {

        super.statementExecuteUpdateBefore(statement,sql);

        try {
            Before(statement,sql);
        }catch (Exception e){

        }


    }

    @Override
    protected void statementExecuteUpdateAfter(StatementProxy statement, String sql, int updateCount) {

        super.statementExecuteUpdateAfter(statement,sql,updateCount);

        try {
            After(statement,sql);
        }catch (Exception e){

        }



    }

    @Override
    protected void statementExecuteQueryBefore(StatementProxy statement, String sql) {

        super.statementExecuteQueryBefore(statement,sql);

        try {
            Before(statement,sql);
        }catch (Exception e){

        }

    }

    @Override
    protected void statementExecuteQueryAfter(StatementProxy statement, String sql, ResultSetProxy resultSet) {

        super.statementExecuteQueryAfter(statement,sql,resultSet);

        try {
            After(statement,sql);
        }catch (Exception e){

        }


    }

    @Override
    protected void statementExecuteBefore(StatementProxy statement, String sql) {

        super.statementExecuteBefore(statement,sql);

        try {
            Before(statement,sql);
        }catch (Exception e){

        }

    }

    @Override
    protected void statementExecuteAfter(StatementProxy statement, String sql, boolean result) {

        super.statementExecuteAfter(statement,sql,result);

        try {
            After(statement,sql);
        }catch (Exception e){

        }



    }

    @Override
    protected void statementExecuteBatchBefore(StatementProxy statement) {

        super.statementExecuteBatchBefore(statement);

        try {
            Before(statement , null);
        }catch (Exception e){

        }


    }

    @Override
    protected void statementExecuteBatchAfter(StatementProxy statement, int[] result) {
        super.statementExecuteBatchAfter(statement , result);

        try {
            After(statement , null);
        }catch (Exception e){

        }

    }

    @Override
    protected void statement_executeErrorAfter(StatementProxy statement, String sql, Throwable error) {

        super.statement_executeErrorAfter(statement , sql , error);

        try {
            ErrorAfter(statement , sql , error);
        }catch (Exception e){

        }

    }


    protected void Before(StatementProxy statement, String sql) {

        Span span = ThreadLocalSpan.CURRENT_TRACER.next();
        if (span == null || span.isNoop()) {
            return;
        }

        if(sql == null){
            sql = statement.getLastExecuteSql();
        }
        // Allow span names of single-word statements like COMMIT

        int spaceIndex = sql.indexOf(' ');
        span.kind(Span.Kind.CLIENT).name(spaceIndex == -1 ? sql : sql.substring(0, spaceIndex));
        span.tag("sql.query", sql);

        parseServerIpAndPort(statement,span);
        span.start();

    }

    protected void After(StatementProxy statement, String sql) {

        Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
        if (span == null || span.isNoop()) {
            return;
        }

        span.finish();

        return ;
    }

    protected void ErrorAfter(StatementProxy statement, String sql, Throwable error) {

        Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
        if (span == null || span.isNoop()) {
            return;
        }

        if (error instanceof SQLException) {
            span.tag("error", Integer.toString(((SQLException) error).getErrorCode()));
        }
        span.finish();

        return ;

    }


    public void parseServerIpAndPort(StatementProxy statement, Span span) {
        try {
            URI url = URI.create(statement.getConnection().getMetaData().getURL().substring(5));
            if (getZipkinServiceName() == null || "".equals(getZipkinServiceName())) {
                try {
                    zipkinServiceName = "DB"+url.getPath();
                }catch (Exception e){
                    ;
                }
            }
            span.remoteServiceName(getZipkinServiceName());
            String host = url.getHost();
            if (host != null) {
                span.remoteIpAndPort(host, url.getPort());
            }
        } catch (Exception e) {
            // remote address is optional
        }
    }

    public String getZipkinServiceName() {
        return zipkinServiceName;
    }

    public void setZipkinServiceName(String zipkinServiceName) {
        this.zipkinServiceName = zipkinServiceName;
    }
}