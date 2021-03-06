/**
 * 
 */
package net.paoding.rose.jade.plugin.sql;

import java.util.List;

import net.paoding.rose.jade.annotation.ReturnGeneratedKeys;
import net.paoding.rose.jade.annotation.SQL;
import net.paoding.rose.jade.annotation.SQLParam;

/**
 * @author Alan.Geng[gengzhi718@gmail.com]
 *
 */
public interface GenericDAO<E, ID> {

    /**
     * 通过主键查询
     * @param id
     */
    E get(ID id);

    /**
     * 保存实体对象
     * @param entity
     * 
     * @return auto increment id
     */
    @ReturnGeneratedKeys
    ID save(@SQLParam("entity") E entity);

    /**
     * 批量保存
     * @param entity
     * 
     * @return 是否更新成功（实现原理：true=如果本次执行影响的行数大于0） 
     */
    boolean save(@SQLParam("entities") List<E> entity);

    /**
     * 更新实体
     * @param entity
     */
    boolean update(@SQLParam("entity") E entity);

    /**
     * 物理删除实体
     * @param id
     */
    @SQL("delete from {table_name} where {primary_key} = :1")
    boolean delete(ID id);
}
