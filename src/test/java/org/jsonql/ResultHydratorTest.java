package org.jsonql;

import org.jsonql.hydrator.ResultHydrator;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ResultHydratorTest {

    @Test
    public void testHydrateNested() throws Exception {
        final List<String> cols = Arrays.asList("id", "name", "author__name");

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
            ResultHydratorTest.class.getClassLoader(),
            new Class[]{ResultSet.class},
            new InvocationHandler() {
                int index = -1;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("next")) {
                        index++;
                        return index < 1; // 1 row
                    }
                    if (method.getName().equals("getMetaData")) {
                        return Proxy.newProxyInstance(
                            ResultHydratorTest.class.getClassLoader(),
                            new Class[]{ResultSetMetaData.class},
                            (p, m, a) -> {
                                if (m.getName().equals("getColumnCount")) return cols.size();
                                if (m.getName().equals("getColumnLabel")) return cols.get((Integer)a[0] - 1);
                                return null;
                            }
                        );
                    }
                    if (method.getName().equals("getObject")) {
                        // ResultHydrator calls getObject(String columnLabel)
                        if (args[0] instanceof String) {
                            String col = (String) args[0];
                            if (col.equals("id")) return 1;
                            if (col.equals("name")) return "Post 1";
                            if (col.equals("author__name")) return "Alice";
                        }
                        return null;
                    }
                    return null;
                }
            }
        );
        
        // Hydrate
        ResultHydrator hydrator = new ResultHydrator();
        List<Map<String, Object>> results = hydrator.hydrate(rs);
        
        assertEquals(1, results.size());
        Map<String, Object> row = results.get(0);
        assertEquals(1, row.get("id"));
        assertEquals("Post 1", row.get("name"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> author = (Map<String, Object>) row.get("author");
        assertEquals("Alice", author.get("name"));
    }
}
