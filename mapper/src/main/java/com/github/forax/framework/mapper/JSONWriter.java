package com.github.forax.framework.mapper;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JSONWriter {

  static class GeneratorCache extends ClassValue<Generator>{

    @Override
    protected Generator computeValue(Class<?> type) {
      var properties = Utils.beanInfo(type).getPropertyDescriptors();
      var generators = Stream.of(properties)
              .filter(property -> !property.getName().equals("class"))
              .filter(property -> property.getReadMethod() != null)
              .<Generator>map(property -> {
                var name = property.getName();
                var getter = property.getReadMethod();

                var jsonProperty = getter.getAnnotation(JSONProperty.class);
                if (jsonProperty != null) {
                  name = jsonProperty.value();
                }

                var prefix = "\"" + name + "\": ";
                return (writer, bean) -> {
                  var value = Utils.invokeMethod(bean, getter);
                  return  prefix + writer.toJSON(value);
                };
              })
              .toList();
      return (writer, bean) -> generators.stream()
              .map(generator -> generator.generate(writer, bean))
              .collect(Collectors.joining(", ", "{", "}"));
    }

  }

  private interface Generator {
    String generate(JSONWriter writer, Object bean);
  }

  private static final GeneratorCache CACHE = new GeneratorCache();

  public String toJSON(Object o) {
    return switch (o){
      case null -> "null";
      case Boolean _, Integer _, Double _ -> "" + o;
      case String s -> "\"" + s + "\"";
      case Object obj -> {
        //var properties = Utils.beanInfo(obj.getClass()).getPropertyDescriptors();
        var generator = CACHE.computeValue(obj.getClass());
        yield generator.generate(this, obj);

        /*
        var joiner = new StringJoiner(", ", "{", "}");
        for (var property : properties){
          var name = property.getName();
          if (name.equals("class")){
            continue;
          }
          var getter = property.getReadMethod();
          if (getter == null){
            continue;
          }
          var value = Utils.invokeMethod(obj, getter);
          joiner.add("\"" + name + "\": " + toJSON(value));
        }
        yield joiner.toString(); */
      }
    };
  }

}
