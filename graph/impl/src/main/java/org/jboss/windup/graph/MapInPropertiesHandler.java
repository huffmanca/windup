package org.jboss.windup.graph;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.jboss.windup.graph.model.WindupVertexFrame;
import org.jboss.windup.util.Logging;
import org.jboss.windup.util.exception.WindupException;

import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.modules.MethodHandler;

/**
 * Handles @MapInProperties Map<String,String>.
 *
 * @author <a href="mailto:ozizka@redhat.com">Ondrej Zizka</a>
 */
public class MapInPropertiesHandler implements MethodHandler<MapInProperties>
{
    private static final Logger log = Logging.get(MapInPropertiesHandler.class);

    @Override
    public Class<MapInProperties> getAnnotationType()
    {
        return MapInProperties.class;
    }

    @Override
    public Object processElement(Object frame, Method method, Object[] args, MapInProperties ann,
                FramedGraph<?> framedGraph, Element elm)
    {
        String methodName = method.getName();
        if (methodName.startsWith("get"))
            return handleGetter((Vertex) elm, method, args, ann, framedGraph);

        if (methodName.startsWith("set"))
            return handleSetter((Vertex) elm, method, args, ann, framedGraph);

        if (methodName.startsWith("put"))
            return handleAdder((Vertex) elm, method, args, ann, framedGraph);

        if (methodName.startsWith("putAll"))
            return handleAdder((Vertex) elm, method, args, ann, framedGraph);

        throw new WindupException("Only get*, set*, and put* method names are supported for @"
                    + MapInProperties.class.getSimpleName() + ", found at: " + method.getName());
    }

    /**
     * Getter
     */
    private Map<String, Object> handleGetter(Vertex vertex, Method method, Object[] args, MapInProperties ann, FramedGraph<?> framedGraph)
    {
        if (args != null && args.length != 0)
            throw new WindupException("Method must take zero arguments");

        Map<String, Object> map = new HashMap<>();
        String prefix = preparePrefix(ann);

        Set<String> keys = vertex.getPropertyKeys();
        for (String key : keys)
        {
            if (!key.startsWith(prefix))
                continue;
            final Object val = vertex.getProperty(key);
            if (!ann.propertyType().isAssignableFrom(val.getClass()))
            {
                log.warning("@InProperties is meant for Map<String,"+ann.propertyType().getName()+">, but the value was: " + val.getClass());
            }

            map.put(key.substring(prefix.length()), val);
        }

        return map;
    }

    /**
     * Setter
     */
    private WindupVertexFrame handleSetter(Vertex vertex, Method method, Object[] args, MapInProperties ann, FramedGraph<?> framedGraph)
    {
        // Argument.
        if (args == null || args.length != 1)
            throw new WindupException("Method must take one argument: " + method.getName());

        if (!(args[0] instanceof Map))
            throw new WindupException("Argument of " + method.getName() + " must be a Map, but is: " + args[0].getClass());

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) args[0];

        String prefix = preparePrefix(ann);

        // For all keys in the old map...
        Set<String> keys = vertex.getPropertyKeys();
        Set<String> mapKeys = map.keySet();
        for (String key : keys)
        {
            if (!key.startsWith(prefix))
                continue;
            if (WindupVertexFrame.TYPE_PROP.equals(key)) // Leave the "type" property.
                continue;
            if (key.startsWith("w:")) // Leave windup internal properties. TODO: Get the prefix from somewhere.
                continue;

            final Object val = vertex.getProperty(key);
            if (!ann.propertyType().isAssignableFrom(val.getClass()))
            {
                log.warning("@InProperties is meant for Map<String,"+ann.propertyType().getName()+">, but the value was: " + val.getClass());
            }
            String subKey = key.substring(prefix.length());
            // ...either change to new value,
            if (map.containsKey(subKey))
            {
                vertex.setProperty(key, map.get(subKey));
                mapKeys.remove(subKey);
            }
            // or remove the old.
            else
                vertex.removeProperty(key);
        }

        // Add the new entries.
        for (String key : mapKeys)
        {
            vertex.setProperty(prefix + key, map.get(key));
        }

        return null;
    }

    /**
     * Adder
     */
    private WindupVertexFrame handleAdder(Vertex vertex, Method method, Object[] args, MapInProperties ann, FramedGraph<?> framedGraph)
    {
        if (args != null && args.length != 1)
            throw new WindupException("Method '" + method.getName() + "' must take one argument, not " + args.length);

        if (args == null || args[0] == null || !(args[0] instanceof Map))
            throw new WindupException("Method '" + method.getName() + "' must take one argument, " +
                    "a Map<String, Serializable> to store in the vertex. Was: " + (args == null || args[0] == null ? "null" : args[0].getClass()));


        String prefix = preparePrefix(ann);

        // Argument.
        @SuppressWarnings("unchecked")
        Map<String, Serializable> map = (Map<String, Serializable>) args[0];

        // Store all map entries in vertex'es properties.
        for (Map.Entry<String, Serializable> entry : map.entrySet())
        {
            final Object value = entry.getValue();
            if (! (value instanceof Serializable))
                throw new WindupException("The values of the map to store in a vertex must all implement Serializable.");
            vertex.setProperty(prefix + entry.getKey(), value);
        }

        return null;
    }

    /**
     * Returns "<ann.propertyPrefix()><SEPAR>", for example, "map:".
     */
    private String preparePrefix(MapInProperties ann)
    {
        return "".equals(ann.propertyPrefix()) ? "" : (ann.propertyPrefix() + MapInProperties.SEPAR);
    }

}
