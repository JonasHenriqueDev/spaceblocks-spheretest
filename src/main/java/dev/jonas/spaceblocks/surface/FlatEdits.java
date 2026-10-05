package dev.jonas.spaceblocks.surface;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** One persistent block address, shared by its core and all guard-apron copies. */
public final class FlatEdits extends SavedData {
    public static final Factory<FlatEdits> FACTORY=new Factory<>(FlatEdits::new,FlatEdits::load);
    private final ConcurrentHashMap<Long,BlockState> blocks=new ConcurrentHashMap<>();
    private volatile int highest=70;
    private volatile int lowest=36;
    public int highestY() { return highest; }
    public int lowestY() { return lowest; }
    public BlockState get(BlockPos p) { return blocks.get(p.asLong()); }
    public java.util.Map<Long,BlockState> snapshot() { return java.util.Map.copyOf(blocks); }
    public void put(BlockPos p,BlockState state) { blocks.put(p.asLong(),state);highest=Math.max(highest,p.getY());lowest=Math.min(lowest,p.getY());setDirty(); }
    private static FlatEdits load(CompoundTag tag,HolderLookup.Provider registries) {
        FlatEdits data=new FlatEdits();
        for(var entry:tag.getList("blocks",Tag.TAG_COMPOUND)) {
            CompoundTag block=(CompoundTag)entry;
            data.put(BlockPos.of(block.getLong("position")),NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),block.getCompound("state")));
        }
        data.setDirty(false);return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        ListTag list=new ListTag();
        blocks.forEach((position,state)->{CompoundTag b=new CompoundTag();b.putLong("position",position);b.put("state",NbtUtils.writeBlockState(state));list.add(b);});
        tag.put("blocks",list);return tag;
    }
}
