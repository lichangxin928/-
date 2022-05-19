# Mybatis



**Mybatis 是什么？**



ORM 框架，把字段映射为对象的属性



例如有一个 User 表

```sql
id int  primary key auto_increment,

name varchar(10) not null,

age int not null
```

变成

```
class User{

	private Integer id;
	private String name;
	private Integer age;
}
```

传统方式

```j
1、导入 JDBC驱动包（通过Class.forName 来加载类）
2、通过DriverManageer来注册驱动
3、建立连接
4、创建 statement
5、CRUD
6、处理结果集
7、关闭资源
```



## 1. Mybatis 如何获取数据源

```java
new SqlSessionFactoryBuilder().build(in);
 >build((InputStream)inputStream, (String)null, (Properties)null);
  >parser.parse()    
   >build(parser.parse()); 
    >parser.evalNode("/configuration")  // 这里会得到 root 信息，全局配置内容
     >parseConfiguration(parser.evalNode("/configuration"));  // 根据 root 信息，对其标签进行解析 
      >environmentsElement(root.evalNode("environments"));
	   >dsFactory = dataSourceElement(child.evalNode("dataSource")); // 这里就是创建数据源，前面还对事物等进行初始化
        >DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance(); // 根据 type 得到
		 >factory.setProperties(props); // 给 factory 填充属性
          >DataSource dataSource = dsFactory.getDataSource() // 返回数据库源
            >Environment.Builder environmentBuilder = new Environment.Builder(id) // id 是 development
              .transactionFactory(txFactory) // 事物Factory
              .dataSource(dataSource); // 数据源
			 >setEnvironment()
```

### 2. Mybatis 如何获取 SQL语句

```java
root.evalNode("mappers")   // 还是parseConfiguration 中的方法，解析到 mappers 标签中的内容
 >mapperElement(root.evalNode("mappers"));  // 可以看到优先级 package > resource > url > class 并且只会处理一个
  >mapperParser.parse(); // （resource）将resource 中的路径利用 resources中的方法来进行获取，得到mapperParser
   >parser.evalNode("/mapper") // 获取 mapper 中的内容
    >configurationElement(parser.evalNode("/mapper")) // 对 mapper 中的内容进行分析
     >context.evalNodes("select|insert|update|delete")	 // 这里会得到一个 list 里面包含了要执行的sql 语句
      >buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
       >buildStatementFromContext(list, null);
        >statementParser.parseStatementNode();  // 这个方法对每一个 list 进行处理
		 >builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets); // 解析结果传入
		  >MappedStatement // 最终一个 crud 的标签会生成一个MappedStatement对象
   
```

### 3. 结果集处理源码分析

```java
factory.openSession();
 >openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false); // 获取默认执行器的类型（simple、reuse、batch）默认为simple
  > newExecutor(Transaction transaction, ExecutorType executorType) // 新建一个执行器
   >return new DefaultSqlSession(configuration, executor, autoCommit); // 返回 sqlSession
    >sqlSession.selectOne; // 使用 sqlSession 来执行语句
	 >this.selectList(statement, parameter); // 处理过程封装成这个函数，最终返回一个list
	  >this.selectList(statement, parameter, RowBounds.DEFAULT)
       >configuration.getMappedStatement(statement);  // 通过传入的 statement 来获取第二步封装好的MappedStatement
        >return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER); // 执行
		 >ms.getBoundSql(parameterObject); // 对 statement 中的sql进行处理，将其中要替换的值改成“？” 并且将值的映射存在parameterMappings 中
		  >createCacheKey(ms, parameterObject, rowBounds, boundSql); // 一级缓存。创建一个CacheKey (hashcode,checksum,sql,id)
           >query(ms, parameterObject, rowBounds, resultHandler, key, boundSql)
            >Cache cache = ms.getCache(); // 创建二级缓存,如果没有创建，就为空，默认创建一级缓存，不创建二级缓存。但是在比较的时候是先在二级缓存中寻找，再到一级缓存中寻找
             >delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql); // 在二级缓存中找到了就直接返回list，没有找到就执行这个方法
			  >handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);// 查询一级缓存中是否存在数据，在此之前会先判一级缓存是否为空，如果为空就将其设置为(List<E>) localCache.getObject(key) 相当于先判断是否是第一次查询
			   >list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql); // 没有的话就从数据库里面查询
				>doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
				 >configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql); // 得到StatementHandler
				  >prepareStatement(handler, ms.getStatementLog());// 在这里面获取Connection
				   >handler.query(stmt, resultHandler)->delegate.query(statement, resultHandler)
  					>resultSetHandler.handleResultSets(ps);
					 >getFirstResultSet(Statement stmt)
					  ->return rs != null ? new ResultSetWrapper(rs, configuration) : null; // 创建一个 ResultSetWrapper
					   >mappedStatement.getResultMaps(); // 得到结果集
					    >handleResultSet(rsw, resultMap, multipleResults, null)
                         >handleRowValues(rsw, resultMap, defaultResultHandler, rowBounds, null);
						  >handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
						   >getRowValue(rsw, discriminatedResultMap, null);
							>applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
							 >metaObject.setValue(mapping.property, value); 最终设置值
```



ResultSetWrapper 类

```java
/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.ObjectTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * @author Iwao AVE!
 */
public class ResultSetWrapper {

  private final ResultSet resultSet;
  private final TypeHandlerRegistry typeHandlerRegistry;
  // 列名
  private final List<String> columnNames = new ArrayList<>();
  // jdbcType所对应的类名 （int --> java.lang.Integer）
  private final List<String> classNames = new ArrayList<>();
  // jdbc 中的 type（varchar，int....）
  private final List<JdbcType> jdbcTypes = new ArrayList<>();
  private final Map<String, Map<Class<?>, TypeHandler<?>>> typeHandlerMap = new HashMap<>();
  private final Map<String, List<String>> mappedColumnNamesMap = new HashMap<>();
  private final Map<String, List<String>> unMappedColumnNamesMap = new HashMap<>();

  // 处理的主要方法
  public ResultSetWrapper(ResultSet rs, Configuration configuration) throws SQLException {
    super();
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.resultSet = rs;
    final ResultSetMetaData metaData = rs.getMetaData();
    final int columnCount = metaData.getColumnCount();
    for (int i = 1; i <= columnCount; i++) {
      columnNames.add(configuration.isUseColumnLabel() ? metaData.getColumnLabel(i) : metaData.getColumnName(i));
      jdbcTypes.add(JdbcType.forCode(metaData.getColumnType(i)));
      classNames.add(metaData.getColumnClassName(i));
    }
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  public List<String> getColumnNames() {
    return this.columnNames;
  }

  public List<String> getClassNames() {
    return Collections.unmodifiableList(classNames);
  }

  public List<JdbcType> getJdbcTypes() {
    return jdbcTypes;
  }

  public JdbcType getJdbcType(String columnName) {
    for (int i = 0 ; i < columnNames.size(); i++) {
      if (columnNames.get(i).equalsIgnoreCase(columnName)) {
        return jdbcTypes.get(i);
      }
    }
    return null;
  }

  /**
   * Gets the type handler to use when reading the result set.
   * Tries to get from the TypeHandlerRegistry by searching for the property type.
   * If not found it gets the column JDBC type and tries to get a handler for it.
   *
   * @param propertyType
   * @param columnName
   * @return
   */
  public TypeHandler<?> getTypeHandler(Class<?> propertyType, String columnName) {
    TypeHandler<?> handler = null;
    Map<Class<?>, TypeHandler<?>> columnHandlers = typeHandlerMap.get(columnName);
    if (columnHandlers == null) {
      columnHandlers = new HashMap<>();
      typeHandlerMap.put(columnName, columnHandlers);
    } else {
      handler = columnHandlers.get(propertyType);
    }
    if (handler == null) {
      JdbcType jdbcType = getJdbcType(columnName);
      handler = typeHandlerRegistry.getTypeHandler(propertyType, jdbcType);
      // Replicate logic of UnknownTypeHandler#resolveTypeHandler
      // See issue #59 comment 10
      if (handler == null || handler instanceof UnknownTypeHandler) {
        final int index = columnNames.indexOf(columnName);
        final Class<?> javaType = resolveClass(classNames.get(index));
        if (javaType != null && jdbcType != null) {
          handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
        } else if (javaType != null) {
          handler = typeHandlerRegistry.getTypeHandler(javaType);
        } else if (jdbcType != null) {
          handler = typeHandlerRegistry.getTypeHandler(jdbcType);
        }
      }
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = new ObjectTypeHandler();
      }
      columnHandlers.put(propertyType, handler);
    }
    return handler;
  }

  private Class<?> resolveClass(String className) {
    try {
      // #699 className could be null
      if (className != null) {
        return Resources.classForName(className);
      }
    } catch (ClassNotFoundException e) {
      // ignore
    }
    return null;
  }

  private void loadMappedAndUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> mappedColumnNames = new ArrayList<>();
    List<String> unmappedColumnNames = new ArrayList<>();
    final String upperColumnPrefix = columnPrefix == null ? null : columnPrefix.toUpperCase(Locale.ENGLISH);
    final Set<String> mappedColumns = prependPrefixes(resultMap.getMappedColumns(), upperColumnPrefix);
    for (String columnName : columnNames) {
      final String upperColumnName = columnName.toUpperCase(Locale.ENGLISH);
      if (mappedColumns.contains(upperColumnName)) {
        mappedColumnNames.add(upperColumnName);
      } else {
        unmappedColumnNames.add(columnName);
      }
    }
    mappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), mappedColumnNames);
    unMappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), unmappedColumnNames);
  }

  public List<String> getMappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    if (mappedColumnNames == null) {
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return mappedColumnNames;
  }

  public List<String> getUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    if (unMappedColumnNames == null) {
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return unMappedColumnNames;
  }

  private String getMapKey(ResultMap resultMap, String columnPrefix) {
    return resultMap.getId() + ":" + columnPrefix;
  }

  private Set<String> prependPrefixes(Set<String> columnNames, String prefix) {
    if (columnNames == null || columnNames.isEmpty() || prefix == null || prefix.length() == 0) {
      return columnNames;
    }
    final Set<String> prefixed = new HashSet<>();
    for (String columnName : columnNames) {
      prefixed.add(prefix + columnName);
    }
    return prefixed;
  }

}

```



```java
SqlSessionFactoryBuilder
    	 	|
    	parse解析
    		|
    	   \|/
	  Configuration
    		|
    	  bulid
    	   \|/
     SqlSessionFactory
    		|
    	openSession
    	   \|/
    	SqlSession
    		|
    	  query
    	   \|/
         Executor
    		|
   	newStatementhandler
    	   \|/
      StatementHandler
    		|
      handleResultSet
    	   \|/
      ResultHandler
    
    
```

