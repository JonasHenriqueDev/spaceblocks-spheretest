package dev.jonas.spaceblocks;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.*;

/** Runtime adapter over wired vanilla density functions. Four samples close both map seams. */
public record PeriodicDensity(DensityFunction source, int width) implements DensityFunction {
  public double compute(FunctionContext context) {
    return PeriodicDomain.sample(
        (x, y, z) -> source.compute(new SinglePointContext(x, y, z)),
        context.blockX(),
        context.blockY(),
        context.blockZ(),
        width);
  }

  public void fillArray(double[] values, ContextProvider contexts) {
    contexts.fillAllDirectly(values, this);
  }

  // The source is already wired. Chunk-local caches must not cache the four translated samples.
  public DensityFunction mapAll(Visitor visitor) {
    return visitor.apply(this);
  }

  public double minValue() {
    return source.minValue();
  }

  public double maxValue() {
    return source.maxValue();
  }

  public KeyDispatchDataCodec<? extends DensityFunction> codec() {
    throw new UnsupportedOperationException("Runtime-only periodic vanilla density");
  }
}
