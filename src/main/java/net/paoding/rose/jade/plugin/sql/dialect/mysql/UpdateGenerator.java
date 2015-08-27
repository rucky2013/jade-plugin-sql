/**
 * 
 */
package net.paoding.rose.jade.plugin.sql.dialect.mysql;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.paoding.rose.jade.plugin.sql.dialect.ISQLGenerator;
import net.paoding.rose.jade.plugin.sql.mapper.IColumnMapper;
import net.paoding.rose.jade.plugin.sql.mapper.IEntityMapper;
import net.paoding.rose.jade.plugin.sql.mapper.IExpandableParameterMapper;
import net.paoding.rose.jade.plugin.sql.mapper.IOperationMapper;
import net.paoding.rose.jade.plugin.sql.mapper.IParameterMapper;
import net.paoding.rose.jade.plugin.sql.mapper.OperationMapper;
import net.paoding.rose.jade.plugin.sql.util.MyLangUtils;
import net.paoding.rose.jade.statement.StatementRuntime;

import org.springframework.dao.InvalidDataAccessApiUsageException;

/**
 * @author Alan.Geng[gengzhi718@gmail.com]
 *
 */
public class UpdateGenerator implements ISQLGenerator<OperationMapper> {

	/* (non-Javadoc)
	 * @see com.cainiao.depot.project.biz.common.jade.dialect.ISQLGenerator#generate(com.cainiao.depot.project.biz.common.jade.mapper.IOperationMapper)
	 */
	@Override
	public String generate(OperationMapper operationMapper, StatementRuntime runtime) {
		Map<String, Object> params = runtime.getParameters();
		if(!operationMapper.getName().equals(IOperationMapper.OPERATION_UPDATE)) {
			throw new InvalidDataAccessApiUsageException("Operation mapper must be a update.");
		}
		
		List<IParameterMapper> parameters = operationMapper.getParameters();
		if(MyLangUtils.isEmpty(parameters)) {
			throw new InvalidDataAccessApiUsageException("Update operation must have parameters.");
		}
		
		IEntityMapper targetEntityMapper = operationMapper.getTargetEntityMapper();
		
		if(parameters.size() == 1 && parameters.get(0).getType().equals(targetEntityMapper.getOriginal())) {
			StringBuilder sql = new StringBuilder(256);
			sql.append("UPDATE ");
			StringBuilder where = new StringBuilder(32);
			sql.append(targetEntityMapper.getName());
			
			IParameterMapper param = parameters.get(0);
			
			if(param instanceof IExpandableParameterMapper) {
				List<IParameterMapper> expandParams = ((IExpandableParameterMapper) param).expand();
				Object entityObject = params.entrySet().iterator().next().getValue();
				
				sql.append(" SET");
				try {
					for(IParameterMapper p : expandParams) {
						IColumnMapper columnMapper = p.getColumnMapper();
						if(columnMapper != null) {
							Field field = p.getColumnMapper().getOriginal();
							Object value = field.get(entityObject);
							if(p.getColumnMapper().isPrimaryKey()) {
								// 主键视为条件
								if(value == null) {
									throw new IllegalArgumentException("Cannot execute update, primary key \"" + p.getColumnMapper().getName() + "\" must not be null.");
								}
								
								if(where.length() > 0) {
									where.append(" AND ");
								} else {
									where.append(" WHERE ");
								}
								
								where.append(p.getName());
								where.append(" = :");
								where.append(p.getOriginalName());
							} else {
								if(value != null) {
									sql.append(" ");
									sql.append(p.getName());
									sql.append(" = ");
									sql.append(":");
									sql.append(p.getOriginalName());
									sql.append(",");
								}
							}
						}
					}
					sql.setLength(sql.length() - 1);
					sql.append(where);
				} catch(IllegalArgumentException e) {
					throw new InvalidDataAccessApiUsageException("", e);
				} catch (Exception e) {
					throw new InvalidDataAccessApiUsageException("Cannot generate sql.", e);
				}
			} else {
				throw new InvalidDataAccessApiUsageException("Auto update operation parameter must be a expandable.");
			}
			
			return sql.toString();
		} else {
			throw new InvalidDataAccessApiUsageException("Please use the entity object to update.");
		}
	}

}
