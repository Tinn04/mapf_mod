package com.tin.mapf.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class GridBuilder {
    private final ServerLevel world;
    private final int feetY; // air layer

    public GridBuilder(ServerLevel world, int feetY) {
        this.world = world;
        this.feetY = feetY;
    }

    public boolean isWalkable(int x, int z) {
        BlockPos floor = new BlockPos(x, feetY - 1, z);
        BlockPos feet  = floor.above();
        BlockPos head  = floor.above(2);

        boolean solidFloor = !world.getBlockState(floor).isAir();
        boolean spaceFeet  = world.isEmptyBlock(feet);
        boolean spaceHead  = world.isEmptyBlock(head);
        return solidFloor && spaceFeet && spaceHead;
    }

    public int feetY() { return feetY; }
    public ServerLevel world() { return world; }
}
