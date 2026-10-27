package com.endersjourney.bteoptimizationfixes.compat.cataclysm;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cataclysm: hace que el Cangrejo de Amatista pueda aparecer dentro de las
 * geodas de amatista del Overworld, ademas de en las Cuevas Frondosas.
 *
 * Cada CHECK_INTERVAL ticks, para cada jugador del Overworld:
 *  1. Busca geodas en los chunks cargados a su alrededor (3 chunks en
 *     horizontal, 32 bloques en vertical) mirando solo la paleta de cada
 *     seccion (si no contiene amatista brotante, ni se lee).
 *  2. La primera vez que una geoda entra en ese radio se decide, UNA sola
 *     vez y para siempre, si tiene guardian (probabilidad GEODE_CRAB_CHANCE).
 *     La decision se guarda en el mundo.
 *  3. Si lo tiene, busca dentro un hueco con suelo de amatista donde quepa
 *     el cangrejo (2.5 x 2.6) y lo hace aparecer como spawn NATURAL, pasando
 *     por los eventos de Forge (In Control, Bad Mobs y AmethystCrabSpawnLimit:
 *     nunca dos cerca). Si no puede en ese momento (no cabe, hay otro
 *     cangrejo cerca...) lo vuelve a intentar en la siguiente comprobacion.
 *
 * Ese cangrejo no desaparece al alejarse (es el "guardian" de la geoda), y
 * cada geoda tiene como mucho un cangrejo en toda la partida.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeodeCrabSpawner {

    /** Cada cuanto se buscan geodas cerca de los jugadores (ticks). 400 = 20 s. */
    private static final int CHECK_INTERVAL = 400;
    /** Probabilidad de que una geoda tenga guardian. Se tira una sola vez por geoda. */
    private static final float GEODE_CRAB_CHANCE = 0.05F; // 1 de cada 20 geodas
    /** Radio de busqueda de geodas alrededor del jugador, en chunks. */
    private static final int SEARCH_RADIUS_CHUNKS = 3;
    /** Altura maxima de las geodas vanilla (generan entre y -58 y 30). */
    private static final int MAX_GEODE_Y = 40;
    /** Distancia vertical maxima entre el jugador y la geoda (bloques). */
    private static final int MAX_VERTICAL_DISTANCE = 32;
    /** Distancia minima al jugador para que no aparezca encima de el. */
    private static final double MIN_PLAYER_DISTANCE = 8.0D;
    /** Dos posiciones a menos de esto se consideran la misma geoda. */
    private static final int SAME_GEODE_DISTANCE = 24;
    /** Hasta donde se busca hueco alrededor de la amatista brotante. */
    private static final int INTERIOR_SEARCH = 8;
    /** Cuantas amatistas brotantes de cada seccion se prueban como punto de partida. */
    private static final int BUDDING_PER_SECTION = 6;

    private GeodeCrabSpawner() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) return;

        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;

        EntityType<?> crabType = AmethystCrabSpawnLimit.getCrabType();
        if (crabType == null) return;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            try {
                String result = trySpawnNear(level, player, crabType, false);
                BTEOptimizationFixes.LOGGER.debug("[bteopt] Geoda ({}): {}", player.getScoreboardName(), result);
            } catch (Exception e) {
                BTEOptimizationFixes.LOGGER.error("[bteopt] Error intentando spawnear un Cangrejo de Amatista en una geoda", e);
            }
        }
    }

    /**
     * Resultado de un intento, para el log y para /bteopt geodecrab.
     * @param force (solo el comando) ignora la tirada: cualquier geoda sin cangrejo vale.
     */
    private static String trySpawnNear(ServerLevel level, ServerPlayer player, EntityType<?> crabType, boolean force) {
        GeodeMemory memory = GeodeMemory.get(level);

        List<SectionPos> sections = findGeodeSections(level, player);
        if (sections.isEmpty()) return "no hay geodas (amatista brotante) en los chunks cargados cercanos";
        Collections.shuffle(sections, new java.util.Random(level.random.nextLong()));

        int withCrab = 0, without = 0, noSpace = 0;
        String lastFail = null;
        for (SectionPos section : sections) {
            List<BlockPos> buddings = findBudding(level, section);
            if (buddings.isEmpty()) continue;
            BlockPos anchor = buddings.get(0);

            GeodeMemory.Entry geode = memory.find(anchor);
            if (geode == null) {
                // Primera vez que se ve esta geoda: una sola tirada para siempre
                boolean guardian = level.random.nextFloat() < GEODE_CRAB_CHANCE;
                geode = memory.add(anchor, guardian ? GeodeMemory.PENDING : GeodeMemory.NO_CRAB);
            }
            if (geode.state == GeodeMemory.SPAWNED) {
                withCrab++;
                continue;
            }
            if (geode.state == GeodeMemory.NO_CRAB && !force) {
                without++;
                continue;
            }

            for (BlockPos budding : buddings) {
                BlockPos spot = findInterior(level, budding, crabType, player);
                if (spot == null) {
                    noSpace++;
                    continue;
                }
                String result = spawnCrab(level, spot, crabType);
                if (result == null) {
                    memory.setState(geode, GeodeMemory.SPAWNED);
                    return "cangrejo aparecido en " + spot.toShortString();
                }
                lastFail = "hueco encontrado en " + spot.toShortString() + " pero " + result;
                break; // se reintenta en la siguiente comprobacion
            }
        }
        if (lastFail != null) return lastFail;
        return sections.size() + " secciones con amatista brotante: " + withCrab + " de geodas que ya tuvieron cangrejo, "
                + without + " de geodas sin guardian, " + noSpace + " puntos sin hueco donde quepa el cangrejo";
    }

    /** Secciones cargadas cerca del jugador cuya paleta contiene amatista brotante. */
    private static List<SectionPos> findGeodeSections(ServerLevel level, ServerPlayer player) {
        List<SectionPos> result = new ArrayList<>();
        int pcx = player.blockPosition().getX() >> 4;
        int pcz = player.blockPosition().getZ() >> 4;
        int py = player.blockPosition().getY();
        int minSection = Math.max(level.getMinSection(), SectionPos.blockToSectionCoord(py - MAX_VERTICAL_DISTANCE));
        int maxSection = Math.min(level.getMaxSection() - 1,
                Math.min(SectionPos.blockToSectionCoord(MAX_GEODE_Y), SectionPos.blockToSectionCoord(py + MAX_VERTICAL_DISTANCE)));
        if (minSection > maxSection) return result;
        int firstSection = level.getMinSection();

        for (int cx = pcx - SEARCH_RADIUS_CHUNKS; cx <= pcx + SEARCH_RADIUS_CHUNKS; cx++) {
            for (int cz = pcz - SEARCH_RADIUS_CHUNKS; cz <= pcz + SEARCH_RADIUS_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] chunkSections = chunk.getSections();
                for (int sy = minSection; sy <= maxSection; sy++) {
                    LevelChunkSection s = chunkSections[sy - firstSection];
                    if (s.hasOnlyAir()) continue;
                    if (s.maybeHas(state -> state.is(Blocks.BUDDING_AMETHYST))) {
                        result.add(SectionPos.of(cx, sy, cz));
                    }
                }
            }
        }
        return result;
    }

    /** Hasta BUDDING_PER_SECTION amatistas brotantes repartidas por la seccion. */
    private static List<BlockPos> findBudding(ServerLevel level, SectionPos section) {
        List<BlockPos> all = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    pos.set(section.minBlockX() + x, section.minBlockY() + y, section.minBlockZ() + z);
                    if (level.getBlockState(pos).is(Blocks.BUDDING_AMETHYST)) all.add(pos.immutable());
                }
            }
        }
        if (all.size() <= BUDDING_PER_SECTION) return all;
        Collections.shuffle(all, new java.util.Random(level.random.nextLong()));
        return all.subList(0, BUDDING_PER_SECTION);
    }

    /** Hueco con suelo de amatista donde quepa el cangrejo, dentro de la geoda. */
    private static BlockPos findInterior(ServerLevel level, BlockPos budding, EntityType<?> crabType, ServerPlayer player) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (BlockPos p : BlockPos.betweenClosed(budding.offset(-INTERIOR_SEARCH, -INTERIOR_SEARCH, -INTERIOR_SEARCH),
                budding.offset(INTERIOR_SEARCH, INTERIOR_SEARCH, INTERIOR_SEARCH))) {
            if (!isOpen(level.getBlockState(p))) continue;
            below.setWithOffset(p, 0, -1, 0);
            if (!isAmethystFloor(level.getBlockState(below))) continue;
            candidates.add(p.immutable());
        }
        Collections.shuffle(candidates, new java.util.Random(level.random.nextLong()));

        for (BlockPos p : candidates) {
            double x = p.getX() + 0.5D;
            double z = p.getZ() + 0.5D;
            if (player.distanceToSqr(x, p.getY(), z) < MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE) continue;
            if (!fits(level, crabType.getAABB(x, p.getY(), z))) continue;
            return p;
        }
        return null;
    }

    /** Aire o racimos/brotes de amatista (las geodas estan llenas: se rompen al salir el cangrejo). */
    private static boolean isOpen(BlockState state) {
        return state.isAir() || state.getBlock() instanceof AmethystClusterBlock;
    }

    private static boolean fits(ServerLevel level, AABB box) {
        int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX - 1.0E-7D);
        int minY = Mth.floor(box.minY), maxY = Mth.floor(box.maxY - 1.0E-7D);
        int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ - 1.0E-7D);
        for (BlockPos p : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!isOpen(level.getBlockState(p))) return false;
        }
        return true;
    }

    private static boolean isAmethystFloor(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.CALCITE);
    }

    /** @return null si ha aparecido; si no, el motivo. */
    private static String spawnCrab(ServerLevel level, BlockPos spot, EntityType<?> crabType) {
        double x = spot.getX() + 0.5D;
        double y = spot.getY();
        double z = spot.getZ() + 0.5D;

        if (AmethystCrabSpawnLimit.hasCrabNearby(level, null, x, y, z)) return "ya hay otro cangrejo a menos de 96 bloques";

        Entity entity = crabType.create(level);
        if (!(entity instanceof Mob crab)) {
            if (entity != null) entity.discard();
            return "no se pudo crear la entidad";
        }
        crab.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);

        // Mismos eventos que un spawn natural (In Control, Bad Mobs, AmethystCrabSpawnLimit...)
        Event.Result result = ForgeEventFactory.canEntitySpawn(crab, level, x, y, z, null, MobSpawnType.NATURAL);
        if (result == Event.Result.DENY) {
            crab.discard();
            return "otro mod ha cancelado el spawn (evento CheckSpawn)";
        }

        // Romper los racimos de amatista que ocupa (sin soltar objetos)
        AABB box = crab.getBoundingBox();
        for (BlockPos p : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX - 1.0E-7D), Mth.floor(box.maxY - 1.0E-7D), Mth.floor(box.maxZ - 1.0E-7D))) {
            if (level.getBlockState(p).getBlock() instanceof AmethystClusterBlock) level.destroyBlock(p, false);
        }

        if (!ForgeEventFactory.doSpecialSpawn(crab, level, (float) x, (float) y, (float) z, null, MobSpawnType.NATURAL)) {
            crab.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.NATURAL, null, null);
        }
        crab.setPersistenceRequired();
        return level.addFreshEntity(crab) ? null : "addFreshEntity lo ha rechazado";
    }

    // --- /bteopt geodecrab: intento inmediato junto al jugador, ignorando la tirada, con diagnostico ---

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bteopt")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("geodecrab").executes(GeodeCrabSpawner::cmdGeodeCrab)));
    }

    private static int cmdGeodeCrab(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.getLevel();
        String msg;
        EntityType<?> crabType = AmethystCrabSpawnLimit.getCrabType();
        if (crabType == null) msg = "Cataclysm no esta cargado";
        else if (level.dimension() != Level.OVERWORLD) msg = "solo funciona en el Overworld";
        else if (level.getDifficulty() == Difficulty.PEACEFUL) msg = "dificultad Pacifica: no aparecen monstruos";
        else if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) msg = "gamerule doMobSpawning = false";
        else msg = trySpawnNear(level, player, crabType, true);
        ctx.getSource().sendSuccess(Component.literal("[bteopt] Geoda: " + msg), false);
        return 1;
    }

    /** Geodas ya vistas y su estado (una amatista brotante de cada una como referencia). */
    private static final class GeodeMemory extends SavedData {
        static final byte NO_CRAB = 0;
        static final byte PENDING = 1;
        static final byte SPAWNED = 2;

        private static final String NAME = BTEOptimizationFixes.MODID + "_geode_crabs";
        private final List<Entry> geodes = new ArrayList<>();

        static final class Entry {
            final BlockPos pos;
            byte state;

            Entry(BlockPos pos, byte state) {
                this.pos = pos;
                this.state = state;
            }
        }

        static GeodeMemory get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(GeodeMemory::load, GeodeMemory::new, NAME);
        }

        static GeodeMemory load(CompoundTag tag) {
            GeodeMemory memory = new GeodeMemory();
            long[] pos = tag.getLongArray("Pos");
            byte[] state = tag.getByteArray("State");
            for (int i = 0; i < pos.length && i < state.length; i++) {
                memory.geodes.add(new Entry(BlockPos.of(pos[i]), state[i]));
            }
            // Formato de la primera version de prueba: solo geodas con cangrejo
            for (long l : tag.getLongArray("Used")) memory.geodes.add(new Entry(BlockPos.of(l), SPAWNED));
            return memory;
        }

        Entry find(BlockPos pos) {
            long maxSq = (long) SAME_GEODE_DISTANCE * SAME_GEODE_DISTANCE;
            for (Entry e : geodes) {
                if (e.pos.distSqr(pos) <= maxSq) return e;
            }
            return null;
        }

        Entry add(BlockPos pos, byte state) {
            Entry e = new Entry(pos, state);
            geodes.add(e);
            setDirty();
            return e;
        }

        void setState(Entry e, byte state) {
            e.state = state;
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            long[] pos = new long[geodes.size()];
            byte[] state = new byte[geodes.size()];
            for (int i = 0; i < pos.length; i++) {
                pos[i] = geodes.get(i).pos.asLong();
                state[i] = geodes.get(i).state;
            }
            tag.put("Pos", new LongArrayTag(pos));
            tag.put("State", new ByteArrayTag(state));
            return tag;
        }
    }
}
