/**
 * 
 */
package net.paoding.rose.jade.plugin.sql;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.Order;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import net.paoding.rose.jade.annotation.SQL;
import net.paoding.rose.jade.plugin.sql.annotations.Table;
import net.paoding.rose.jade.plugin.sql.dialect.IDialect;
import net.paoding.rose.jade.plugin.sql.dialect.MySQLDialect;
import net.paoding.rose.jade.plugin.sql.mapper.EntityMapperManager;
import net.paoding.rose.jade.plugin.sql.mapper.IOperationMapper;
import net.paoding.rose.jade.plugin.sql.mapper.OperationMapperManager;
import net.paoding.rose.jade.plugin.sql.util.BasicSQLFormatter;
import net.paoding.rose.jade.plugin.sql.util.PlumUtils;
import net.paoding.rose.jade.statement.DAOMetaData;
import net.paoding.rose.jade.statement.Interpreter;
import net.paoding.rose.jade.statement.StatementMetaData;
import net.paoding.rose.jade.statement.StatementRuntime;

/**
 * Plum插件用于生成SQL的拦截器。
 * 
 * @author Alan.Geng[gengzhi718@gmail.com]
 */
@Order(-1)
public class PlumSQLInterpreter implements Interpreter, InitializingBean, ApplicationContextAware {

    private static final Log logger = LogFactory.getLog(PlumSQLInterpreter.class);

    private ApplicationContext applicationContext;

    private OperationMapperManager operationMapperManager;

    private IDialect dialect;

    public void setDialect(IDialect dialect) {
        this.dialect = dialect;
    }

    public void setOperationMapperManager(OperationMapperManager operationMapperManager) {
        this.operationMapperManager = operationMapperManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (operationMapperManager == null) {
            operationMapperManager = new OperationMapperManager();
            operationMapperManager.setEntityMapperManager(new EntityMapperManager());
        }
        if (dialect == null) {
            // 将来可能扩展点:不同的DAO可以有不同的Dialect哦，而且是自动知道，不需要外部设置。
            dialect = new MySQLDialect();
        }
        // 
        if (logger.isInfoEnabled()) {
            String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(//
                applicationContext, GenericDAO.class);
            logger.info("[jade-plugin-sql] found " + beanNames.length + " GenericDAOs: "
                        + Arrays.toString(beanNames));
        }

    }

    /**
     * 对 {@link GenericDAO} 及其子DAO接口中没有注解&reg;SQL或仅仅&reg;SQL("")的方法进行解析，根据实际参数情况自动动态生成SQL语句
     */
    @Override
    public void interpret(StatementRuntime runtime) {
        final String interpreterAttribute = "jade-plugin-sql.interpreter";
        Interpreter interpreter = runtime.getMetaData().getAttribute(interpreterAttribute);
        if (interpreter == null) {
            StatementMetaData smd = runtime.getMetaData();
            synchronized (smd) {
                interpreter = smd.getAttribute(interpreterAttribute);
                if (interpreter == null) {
                    interpreter = PassThroughInterpreter;
                    if (GenericDAO.class.isAssignableFrom(smd.getDAOMetaData().getDAOClass())) {
                        interpreter = VariableResolverInterpreter;
                        SQL sqlAnnotation = smd.getMethod().getAnnotation(SQL.class);
                        if (sqlAnnotation == null // 没有注解@SQL
                            || PlumUtils.isBlank(sqlAnnotation.value()) // 虽注解但没有写SQL
                            || "jade-plugin-sql".equals(sqlAnnotation.value())) // 明确表示使用jade-plugin-sql
                        {
                            IOperationMapper mapper = operationMapperManager.create(smd);
                            interpreter = new SQLGeneratorInterpreter(mapper);
                        }
                    }
                    smd.setAttribute(interpreterAttribute, interpreter);
                }
            }
        }
        interpreter.interpret(runtime);
    }

    /**
     * 透传SQL解析器
     */
    private static final Interpreter PassThroughInterpreter = new Interpreter() {

        @Override
        public void interpret(StatementRuntime runtime) {
            //
        }

    };

    /**
     * 实际SQL解析器
     *
     */
    private class SQLGeneratorInterpreter implements Interpreter {

        final IOperationMapper operationMapper;

        public SQLGeneratorInterpreter(IOperationMapper operationMapper) {
            this.operationMapper = operationMapper;
        }

        @Override
        public void interpret(StatementRuntime runtime) {
            try {
                String sql = dialect.translate(operationMapper, runtime);
                if (logger.isInfoEnabled()) {
                    BasicSQLFormatter formatter = new BasicSQLFormatter();
                    logger.info("Plum auto generated by " + dialect.getClass().getSimpleName() + ":"
                                + formatter.format(sql));
                }
                runtime.setSQL(sql);
            } catch (Exception e) {
                throw new InvalidDataAccessApiUsageException(e.getMessage(), e);
            }
        }

    };

    /**
     * 变量解析器（表名、主键名等）
     * -- 以下为临时性hardcode代码，不可维护的代码，不和谐的代码，搬到哪里去才能漂亮呢？@Alan
     */
    private static final Interpreter VariableResolverInterpreter = new Interpreter() {

        @Override
        public void interpret(StatementRuntime runtime) {
            // 临时hard代码2-1：获取table_name替换原始SQL中的{table_name}变量
            String sql = runtime.getSQL();
            // 替换表名
            if (sql.contains("{table_name}")) {
                StatementMetaData smd = runtime.getMetaData();
                DAOMetaData dmd = smd.getDAOMetaData();
                final String tableNameAttribute = "jade-plugin-sql.interpreter";
                String tableName = dmd.getAttribute(tableNameAttribute);
                if (tableName == null) {
                    Class<?> entityType = smd.getDAOMetaData().resolveTypeVariable(//
                        GenericDAO.class, "E");
                    Table tableAnnotation = entityType.getAnnotation(Table.class);
                    if (tableAnnotation != null) {
                        tableName = tableAnnotation.value();
                    }
                    if (PlumUtils.isBlank(tableName)) {
                        tableName = entityType.getSimpleName().substring(0,
                            entityType.getSimpleName().length() - 2);
                        tableName = generateName(tableName);
                    }
                    dmd.setAttribute(tableNameAttribute, tableName);
                }
                sql = sql.replace("{table_name}", tableName);
                runtime.setSQL(sql);
            }
            // 替换主键
            if (sql.contains("{primary_key}")) {
                //TODO: 假装pk都是“id”
                sql = sql.replace("{primary_key}", "id");
                runtime.setSQL(sql);
            }

            // 临时hard代码2-1结束

        }

        // 临时代码2-2，copied from AbstractMapper#generateName
        private String generateName(String source) {
            if (PlumUtils.isBlank(source)) {
                return null;
            }

            if (source.matches("^[a-zA-Z\\\\.]+$")) {
                StringBuilder result = new StringBuilder();

                for (int i = 0; i < source.length(); i++) {
                    char c = source.charAt(i);

                    if (Character.isWhitespace(c)) {
                        continue;
                    }

                    if (Character.isUpperCase(c)) {
                        if (result.length() > 0) {
                            result.append("_");
                        }
                        result.append(Character.toLowerCase(c));
                    } else {
                        result.append(c);
                    }
                }

                return result.toString();
            } else {
                throw new IllegalArgumentException("Illegal naming conventions.");
            }
        }
    };

}
