package io.vertxrpc0.conf;

import com.esotericsoftware.kryo.util.IntMap;
import com.google.common.base.Preconditions;
import com.google.common.reflect.ClassPath;
import io.vertxrpc0.annotation.TrustedType;
import io.vertxrpc0.kryo.KryoRegistry;
import io.vertxrpc0.kryo.TrustedTypeKryoRegistry;
import io.vertxrpc0.transport.MarkedLenMessageHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @author fishzhao
 * @since 2022-01-04
 */
@Slf4j
@SuppressWarnings({"UnusedReturnValue"})
public abstract class AbstractConfigurator<T extends AbstractConfigurator<T>> {

  @Getter(AccessLevel.PROTECTED)
  private final ClassLoader classLoader;

  private final IntMap<Class<?>> typeRegistry = new IntMap<>();

  private KryoRegistry kryoRegistry;

  @Getter(AccessLevel.PROTECTED)
  private int maxMsgLen = MarkedLenMessageHandler.DEFAULT_MAX_MSG_LEN;

  protected AbstractConfigurator(@NonNull ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  // Prefer the thread-context ClassLoader (set by frameworks / isolated Vert.x deployments to the
  // app CL that can see user @TrustedType classes); fall back only when none is set.
  protected static ClassLoader defaultClassLoader(ClassLoader fallback) {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    return contextClassLoader != null ? contextClassLoader : fallback;
  }

  public final T registerType(@NonNull Class<?> type, int typeId) {
    Preconditions.checkArgument(
        !type.isLocalClass()
            && !type.isPrimitive()
            && !type.isInterface()
            && !type.isArray()
            && !type.isAnnotation()
            && !type.isAnonymousClass()
            && !type.isSynthetic(),
        "Unsupported type: %s",
        type.getTypeName());
    Preconditions.checkArgument(typeId >= 0, "typeId should >= 0: %s, %s", type, typeId);
    Class<?> old = typeRegistry.put(typeId, type);
    Preconditions.checkArgument(
        old == null || old.equals(type),
        "Duplicated typeId for \"%s\" and \"%s\": %s",
        old,
        type,
        typeId);
    log.info("Register bean class: {} -> {}", typeId, type.getTypeName());
    return self();
  }

  public final T registerType(@NonNull Class<?> type) {
    Preconditions.checkArgument(
        type.isAnnotationPresent(TrustedType.class),
        "@%s is required for type %s!",
        TrustedType.class.getSimpleName(),
        type);
    return registerType(type, type.getAnnotation(TrustedType.class).typeId());
  }

  public final T registerTypes(@NonNull String packageName, boolean recursive) {
    visitPackage(
        packageName,
        recursive,
        type -> {
          if (type.isAnnotationPresent(TrustedType.class)) {
            registerType(type);
          }
        });
    return self();
  }

  @SuppressWarnings("unchecked")
  private T self() {
    return (T) this;
  }

  protected void visitPackage(
      @NonNull String packageName,
      boolean recursive,
      @NonNull Consumer<? super Class<?>> typeHandler) {
    Set<ClassPath.ClassInfo> classInfoSet;
    try {
      if (recursive) {
        classInfoSet = ClassPath.from(classLoader).getTopLevelClassesRecursive(packageName);
      } else {
        classInfoSet = ClassPath.from(classLoader).getTopLevelClasses(packageName);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (ClassPath.ClassInfo classInfo : classInfoSet) {
      Class<?> topLevel = classInfo.load();
      typeHandler.accept(topLevel);
      for (Class<?> inner : topLevel.getDeclaredClasses()) {
        typeHandler.accept(inner);
      }
    }
  }

  protected final KryoRegistry getKryoRegistry() {
    return kryoRegistry == null ? new TrustedTypeKryoRegistry(typeRegistry) : kryoRegistry;
  }

  public final T setKryoRegistry(KryoRegistry kryoRegistry) {
    if (kryoRegistry != null && !typeRegistry.isEmpty()) {
      log.warn("Use custom kryo registry, preregistered types disposed： {}", typeRegistry);
      typeRegistry.clear();
    }
    this.kryoRegistry = kryoRegistry;
    return self();
  }

  /**
   * Sets the maximum length, in bytes, of a single inbound message frame (i.e. an RPC response).
   * Frames whose declared length exceeds this are rejected before their body is buffered, bounding
   * per-connection memory. Defaults to {@link MarkedLenMessageHandler#DEFAULT_MAX_MSG_LEN}.
   */
  public final T setMaxMsgLen(int maxMsgLen) {
    Preconditions.checkArgument(maxMsgLen > 0, "maxMsgLen must be positive: %s", maxMsgLen);
    this.maxMsgLen = maxMsgLen;
    return self();
  }
}
