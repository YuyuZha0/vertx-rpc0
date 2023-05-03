package com.github.rpc0.kryo.serializer;

/**
 * @author fishzhao
 * @since 2022-02-18
 */
final class RuntimeTypeTrait {

  private RuntimeTypeTrait() {
    throw new IllegalStateException();
  }

  //element should not be null
  static Class<?> elementType(Iterable<?> iterable) {
    Class<?> elementType = null;
    for (Object element : iterable) {
      if (elementType != null) {
        if (elementType != element.getClass()) {
          elementType = null;
          break;
        }
      } else {
        elementType = element.getClass();
      }
    }
    return elementType;
  }

}
