package com.dragonminez.server.world.structure;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.server.world.gen.DemonRealmGeneration;
import com.dragonminez.server.world.structure.helper.MainStructureTypes;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fork 1.20.2: do {@code BossStructures} da v2.2 (d824a29c) so veio o Berco de Chifres do Gomah, o covil dele no
 * Primeiro Mundo do Reino Demoniaco ({@code GomahCradleStructure} e {@code GomahCradleShape}, copiados linha a
 * linha). As outras estruturas de boss da v2.2 (Arvore do Poder, Estrela Gete, cratera Saiyajin e o acampamento
 * do Gomah, que e do raid) nao vieram.
 */
public final class BossStructures {

	private BossStructures() {}

	public static class GomahCradleStructure extends Structure {
		public static final Codec<GomahCradleStructure> CODEC = simpleCodec(GomahCradleStructure::new);

		public GomahCradleStructure(StructureSettings settings) {
			super(settings);
		}

		@Override
		protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			ChunkPos chunkPos = context.chunkPos();
			int centerX = chunkPos.getMiddleBlockX();
			int centerZ = chunkPos.getMiddleBlockZ();
			int surface = GomahCradleShape.surfaceFor(context.chunkGenerator(), context.heightAccessor(), context.randomState(), centerX, centerZ);
			GomahCradleShape shape = GomahCradleShape.create(centerX, centerZ, GomahCradleShape.seedFor(context.seed(), chunkPos), surface);
			Piece piece = new Piece(shape);
			return Optional.of(new GenerationStub(new BlockPos(centerX, surface, centerZ), builder -> builder.addPiece(piece)));
		}

		@Override
		public StructureType<?> type() {
			return MainStructureTypes.GOMAH_CRADLE.get();
		}

		public static class Piece extends StructurePiece {
			private final int version;
			private final long seed;
			private final int centerX;
			private final int centerZ;
			private final int surfaceY;
			private volatile GomahCradleShape shape;

			public Piece(GomahCradleShape shape) {
				super(MainStructureTypes.GOMAH_CRADLE_PIECE.get(), 0,
						new BoundingBox(shape.minX(), shape.minY(), shape.minZ(), shape.maxX(), shape.maxY(), shape.maxZ()));
				this.version = GomahCradleShape.VERSION;
				this.seed = shape.seed();
				this.centerX = shape.centerX();
				this.centerZ = shape.centerZ();
				this.surfaceY = shape.surfaceY();
				this.shape = shape;
				GomahCradleShape.remember(shape);
			}

			public Piece(CompoundTag tag) {
				super(MainStructureTypes.GOMAH_CRADLE_PIECE.get(), tag);
				this.version = tag.getInt("ShapeVersion");
				this.seed = tag.getLong("Seed");
				this.centerX = tag.getInt("CenterX");
				this.centerZ = tag.getInt("CenterZ");
				this.surfaceY = tag.getInt("SurfaceY");
			}

			@Override
			protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
				tag.putInt("ShapeVersion", this.version);
				tag.putLong("Seed", this.seed);
				tag.putInt("CenterX", this.centerX);
				tag.putInt("CenterZ", this.centerZ);
				tag.putInt("SurfaceY", this.surfaceY);
			}

			public GomahCradleShape shape() {
				GomahCradleShape current = this.shape;
				if (current != null) return current;
				current = GomahCradleShape.cached(this.seed, this.centerX, this.centerZ, this.surfaceY);
				if (current == null) {
					current = GomahCradleShape.create(this.centerX, this.centerZ, this.seed, this.surfaceY);
					GomahCradleShape.remember(current);
				}
				this.shape = current;
				return current;
			}

			@Override
			public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
									RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
				GomahCradleShape shape = this.shape();
				int minX = Math.max(Math.max(box.minX(), chunkPos.getMinBlockX()), shape.minX());
				int maxX = Math.min(Math.min(box.maxX(), chunkPos.getMaxBlockX()), shape.maxX());
				int minZ = Math.max(Math.max(box.minZ(), chunkPos.getMinBlockZ()), shape.minZ());
				int maxZ = Math.min(Math.min(box.maxZ(), chunkPos.getMaxBlockZ()), shape.maxZ());
				int minY = Math.max(box.minY(), shape.minY());
				int maxY = Math.min(box.maxY(), shape.maxY());
				if (minX > maxX || minZ > maxZ || minY > maxY) return;

				GomahCradleShape.Region region = shape.classify(minX, minY, minZ, maxX, maxY, maxZ);
				BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
				for (int x = minX; x <= maxX; x++) {
					for (int z = minZ; z <= maxZ; z++) {
						for (int y = minY; y <= maxY; y++) {
							byte code = region.get(x, y, z);
							if (code == GomahCradleShape.NONE) continue;
							BlockState current = level.getBlockState(cursor.set(x, y, z));
							if (current.is(Blocks.BEDROCK) || current.hasBlockEntity() || current.is(MainBlocks.DEMON_REALM_CRUST.get())) continue;
							BlockState next = blockFor(code);
							if (code == GomahCradleShape.AIR || code == GomahCradleShape.LIGHT) {
								if (!current.isAir() && code == GomahCradleShape.LIGHT) continue;
								if (current.isAir() && code == GomahCradleShape.AIR) continue;
							}
							if (current == next) continue;
							level.setBlock(cursor, next, 2);
						}
					}
				}
			}

			private static BlockState blockFor(byte code) {
				return switch (code) {
					case GomahCradleShape.AIR -> Blocks.AIR.defaultBlockState();
					case GomahCradleShape.TOPSOIL -> MainBlocks.RED_ASH.get().defaultBlockState();
					case GomahCradleShape.MAGMA -> MainBlocks.DEMON_MAGMA_ROCK.get().defaultBlockState();
					case GomahCradleShape.HORN -> MainBlocks.DEMON_HORN.get().defaultBlockState();
					case GomahCradleShape.GLOW -> MainBlocks.DEMON_HORN_GLOW.get().defaultBlockState();
					case GomahCradleShape.BRICK -> MainBlocks.DEMON_BRICKS.get().defaultBlockState();
					case GomahCradleShape.UNDER -> Blocks.BLACKSTONE.defaultBlockState();
					case GomahCradleShape.LIGHT -> Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
					default -> MainBlocks.DEMON_ROCK.get().defaultBlockState();
				};
			}
		}
	}

	public static final class GomahCradleShape {
		public static final int VERSION = 1;
		public static final ChunkPos ORIGIN = new ChunkPos(-70, -72);

		public static final byte NONE = 0;
		public static final byte AIR = 1;
		public static final byte ROCK = 2;
		public static final byte TOPSOIL = 3;
		public static final byte MAGMA = 4;
		public static final byte HORN = 5;
		public static final byte GLOW = 6;
		public static final byte BRICK = 7;
		public static final byte UNDER = 8;
		public static final byte LIGHT = 9;

		public static final int ISLAND_RADIUS = 62;
		public static final int DAIS_RADIUS = 7;
		public static final int MIN_SURFACE = 118;
		public static final int MAX_SURFACE = 136;
		public static final int CEILING = DemonRealmGeneration.CRUST_A_BOTTOM - DemonRealmGeneration.STALACTITE_DEPTH - 4;
		public static final int GROUND_CLEARANCE = 38;
		public static final int REACH = ISLAND_RADIUS + 34;

		private static final int UNDER_DEPTH = 42;
		private static final int UNDER_MIN = 6;
		private static final int HORN_COUNT = 18;
		private static final int HORN_SAMPLES = 40;
		private static final double HORN_BASE_RADIUS = 7.0D;
		private static final double GLOW_SHELL = 1.4D;
		private static final double GLOW_FACING = 0.2D;
		private static final int SPIKE_COUNT = 26;
		private static final int STALACTITE_COUNT = 36;
		private static final int MAX_STALACTITE = 30;
		private static final int LIGHT_SPACING = 6;
		private static final int THRONE_BACK = 4;
		private static final int THRONE_HEIGHT = 7;
		private static final int CACHE_LIMIT = 4;
		private static final int[][] SURFACE_SAMPLES = {{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}, {28, 28}, {-28, 28}, {28, -28}, {-28, -28},
				{70, 0}, {-70, 0}, {0, 70}, {0, -70}, {50, 50}, {-50, 50}, {50, -50}, {-50, -50}};
		private static final Map<Key, GomahCradleShape> CACHE = new ConcurrentHashMap<>();

		private record Key(long seed, int centerX, int centerZ, int surfaceY, int version) {}

		private record Cone(double baseX, double baseY, double baseZ, double tipX, double tipY, double tipZ, double radius, byte code,
							int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			static Cone of(double baseX, double baseY, double baseZ, double tipX, double tipY, double tipZ, double radius, byte code) {
				double pad = radius + 1.0D;
				return new Cone(baseX, baseY, baseZ, tipX, tipY, tipZ, radius, code,
						(int) Math.floor(Math.min(baseX, tipX) - pad), (int) Math.floor(Math.min(baseY, tipY) - pad), (int) Math.floor(Math.min(baseZ, tipZ) - pad),
						(int) Math.ceil(Math.max(baseX, tipX) + pad), (int) Math.ceil(Math.max(baseY, tipY) + pad), (int) Math.ceil(Math.max(baseZ, tipZ) + pad));
			}
		}

		private record Horn(double[] xs, double[] ys, double[] zs, double[] radii, double outX, double outZ,
							int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

		public static final class Region {
			private final int minX;
			private final int minY;
			private final int minZ;
			private final int sizeX;
			private final int sizeY;
			private final int sizeZ;
			private final byte[] codes;

			Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
				this.minX = minX;
				this.minY = minY;
				this.minZ = minZ;
				this.sizeX = maxX - minX + 1;
				this.sizeY = maxY - minY + 1;
				this.sizeZ = maxZ - minZ + 1;
				this.codes = new byte[this.sizeX * this.sizeY * this.sizeZ];
			}

			private int index(int x, int y, int z) {
				int lx = x - this.minX;
				int ly = y - this.minY;
				int lz = z - this.minZ;
				if (lx < 0 || ly < 0 || lz < 0 || lx >= this.sizeX || ly >= this.sizeY || lz >= this.sizeZ) return -1;
				return (lx * this.sizeZ + lz) * this.sizeY + ly;
			}

			public byte get(int x, int y, int z) {
				int i = this.index(x, y, z);
				return i < 0 ? NONE : this.codes[i];
			}

			void set(int x, int y, int z, byte code) {
				int i = this.index(x, y, z);
				if (i >= 0) this.codes[i] = code;
			}

			void raise(int x, int y, int z, byte code) {
				int i = this.index(x, y, z);
				if (i < 0) return;
				byte current = this.codes[i];
				if (current == HORN || (current == GLOW && code != HORN) || current == BRICK) return;
				this.codes[i] = code;
			}

			int maxX() {
				return this.minX + this.sizeX - 1;
			}

			int maxY() {
				return this.minY + this.sizeY - 1;
			}

			int maxZ() {
				return this.minZ + this.sizeZ - 1;
			}
		}

		private final long seed;
		private final int centerX;
		private final int centerZ;
		private final int surfaceY;
		private final double[] edgeAmp = new double[3];
		private final double[] edgePhase = new double[3];
		private final List<Cone> stalactites = new ArrayList<>();
		private final List<Cone> spikes = new ArrayList<>();
		private final List<Horn> horns = new ArrayList<>();
		private final int minY;
		private final int maxY;

		private GomahCradleShape(int centerX, int centerZ, long seed, int surfaceY) {
			this.seed = seed;
			this.centerX = centerX;
			this.centerZ = centerZ;
			this.surfaceY = surfaceY;
			Random random = new Random(seed * 0x9E3779B97F4A7C15L + VERSION);

			int[] frequencies = {3, 7, 13};
			double[] amplitudes = {4.0D, 2.5D, 1.5D};
			for (int i = 0; i < 3; i++) {
				this.edgeAmp[i] = amplitudes[i] * (0.7D + random.nextDouble() * 0.6D);
				this.edgePhase[i] = random.nextDouble() * Math.PI * 2.0D + frequencies[i];
			}

			this.buildHorns(random);
			this.buildSpikes(random);
			this.buildStalactites(random);

			int low = surfaceY - UNDER_MIN - UNDER_DEPTH - 6 - MAX_STALACTITE;
			for (Horn horn : this.horns) low = Math.min(low, horn.minY());
			this.minY = low;
			this.maxY = CEILING + 2;
		}

		public static GomahCradleShape create(int centerX, int centerZ, long seed, int surfaceY) {
			return new GomahCradleShape(centerX, centerZ, seed, surfaceY);
		}

		public static GomahCradleShape cached(long seed, int centerX, int centerZ, int surfaceY) {
			return CACHE.get(new Key(seed, centerX, centerZ, surfaceY, VERSION));
		}

		public static void remember(GomahCradleShape shape) {
			if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
			CACHE.put(new Key(shape.seed, shape.centerX, shape.centerZ, shape.surfaceY, VERSION), shape);
		}

		public static long seedFor(long levelSeed, ChunkPos origin) {
			WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
			random.setLargeFeatureSeed(levelSeed, origin.x, origin.z);
			return random.nextLong();
		}

		public static int surfaceFor(ChunkGenerator generator, LevelHeightAccessor heights, RandomState randomState, int centerX, int centerZ) {
			int ground = DemonRealmGeneration.FIRST_BASE;
			for (int[] offset : SURFACE_SAMPLES) {
				NoiseColumn column = generator.getBaseColumn(centerX + offset[0], centerZ + offset[1], heights, randomState);
				for (int y = CEILING; y > DemonRealmGeneration.FIRST_BASE - 16; y--) {
					BlockState state = column.getBlock(y);
					if (state.isAir() || !state.getFluidState().isEmpty()) continue;
					ground = Math.max(ground, y);
					break;
				}
			}
			return Math.max(MIN_SURFACE, Math.min(MAX_SURFACE, ground + GROUND_CLEARANCE));
		}

		public static GomahCradleShape planned(ServerLevel level) {
			ChunkGenerator generator = level.getChunkSource().getGenerator();
			RandomState randomState = level.getChunkSource().randomState();
			long seed = seedFor(level.getSeed(), ORIGIN);
			int centerX = ORIGIN.getMiddleBlockX();
			int centerZ = ORIGIN.getMiddleBlockZ();
			int surface = surfaceFor(generator, level, randomState, centerX, centerZ);
			GomahCradleShape shape = cached(seed, centerX, centerZ, surface);
			if (shape != null) return shape;
			shape = create(centerX, centerZ, seed, surface);
			remember(shape);
			return shape;
		}

		public static double distanceFromCradle(int x, int z) {
			double dx = x - ORIGIN.getMiddleBlockX();
			double dz = z - ORIGIN.getMiddleBlockZ();
			return Math.sqrt(dx * dx + dz * dz);
		}

		public long seed() {
			return this.seed;
		}

		public int centerX() {
			return this.centerX;
		}

		public int centerZ() {
			return this.centerZ;
		}

		public int surfaceY() {
			return this.surfaceY;
		}

		public int minX() {
			return this.centerX - REACH;
		}

		public int maxX() {
			return this.centerX + REACH;
		}

		public int minZ() {
			return this.centerZ - REACH;
		}

		public int maxZ() {
			return this.centerZ + REACH;
		}

		public int minY() {
			return this.minY;
		}

		public int maxY() {
			return this.maxY;
		}

		public BlockPos anchor() {
			return new BlockPos(this.centerX, this.surfaceY + 1, this.centerZ);
		}

		public BlockPos approach() {
			return new BlockPos(this.centerX, this.surfaceY + 2, this.centerZ + ISLAND_RADIUS - 14);
		}

		public double edgeRadius(double angle) {
			double r = ISLAND_RADIUS;
			int[] frequencies = {3, 7, 13};
			for (int i = 0; i < 3; i++) r += this.edgeAmp[i] * Math.sin(angle * frequencies[i] + this.edgePhase[i]);
			return r;
		}

		private void buildHorns(Random random) {
			double twist = (random.nextBoolean() ? 1.0D : -1.0D) * (4.0D + random.nextDouble() * 3.0D);
			double offset = random.nextDouble() * Math.PI * 2.0D;
			for (int i = 0; i < HORN_COUNT; i++) {
				double angle = offset + Math.PI * 2.0D * i / HORN_COUNT + (random.nextDouble() - 0.5D) * 0.08D;
				double size = (i % 2 == 0 ? 1.0D : 0.8D) * (0.94D + random.nextDouble() * 0.08D);
				double height = (CEILING - 2 - this.surfaceY) * size;
				double edge = this.edgeRadius(angle);
				double cos = Math.cos(angle);
				double sin = Math.sin(angle);

				double[] rho = {edge - 9.0D, edge + 26.0D, edge + 21.0D, edge * 0.42D};
				double[] lift = {-26.0D, 4.0D, height * 0.6D, height};
				double baseRadius = HORN_BASE_RADIUS * Math.sqrt(size) + (random.nextDouble() - 0.5D) * 1.2D;

				double[] xs = new double[HORN_SAMPLES + 1];
				double[] ys = new double[HORN_SAMPLES + 1];
				double[] zs = new double[HORN_SAMPLES + 1];
				double[] radii = new double[HORN_SAMPLES + 1];
				double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
				double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
				for (int s = 0; s <= HORN_SAMPLES; s++) {
					double t = s / (double) HORN_SAMPLES;
					double r = bezier(rho, t);
					double y = this.surfaceY + bezier(lift, t);
					double side = twist * t * t;
					xs[s] = this.centerX + 0.5D + cos * r - sin * side;
					zs[s] = this.centerZ + 0.5D + sin * r + cos * side;
					ys[s] = y;
					radii[s] = baseRadius * Math.pow(1.0D - t, 0.8D) + 0.45D;
					minX = Math.min(minX, xs[s] - radii[s]);
					maxX = Math.max(maxX, xs[s] + radii[s]);
					minY = Math.min(minY, ys[s] - radii[s]);
					maxY = Math.max(maxY, ys[s] + radii[s]);
					minZ = Math.min(minZ, zs[s] - radii[s]);
					maxZ = Math.max(maxZ, zs[s] + radii[s]);
				}
				this.horns.add(new Horn(xs, ys, zs, radii, cos, sin, (int) Math.floor(minX) - 1, (int) Math.floor(minY) - 1, (int) Math.floor(minZ) - 1,
						(int) Math.ceil(maxX) + 1, (int) Math.ceil(maxY) + 1, (int) Math.ceil(maxZ) + 1));
			}
		}

		private static double bezier(double[] p, double t) {
			double u = 1.0D - t;
			return u * u * u * p[0] + 3.0D * u * u * t * p[1] + 3.0D * u * t * t * p[2] + t * t * t * p[3];
		}

		private void buildSpikes(Random random) {
			int placed = 0;
			for (int attempt = 0; attempt < SPIKE_COUNT * 6 && placed < SPIKE_COUNT; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double edge = this.edgeRadius(angle);
				double distance = 17.0D + random.nextDouble() * (edge - 25.0D);
				double x = this.centerX + 0.5D + Math.cos(angle) * distance;
				double z = this.centerZ + 0.5D + Math.sin(angle) * distance;
				boolean tooClose = false;
				for (Cone spike : this.spikes) {
					double dx = spike.baseX() - x;
					double dz = spike.baseZ() - z;
					if (dx * dx + dz * dz < 81.0D) {
						tooClose = true;
						break;
					}
				}
				if (tooClose) continue;
				double outer = distance / edge;
				double height = 8.0D + random.nextDouble() * 10.0D + outer * 8.0D;
				double radius = 1.8D + random.nextDouble() * 1.6D + outer * 1.2D;
				double lean = 0.15D + random.nextDouble() * 0.15D;
				double base = this.surfaceY - 1.0D;
				this.spikes.add(Cone.of(x, base, z, x + Math.cos(angle) * height * lean, base + height, z + Math.sin(angle) * height * lean, radius, HORN));
				placed++;
			}
		}

		private void buildStalactites(Random random) {
			for (int i = 0; i < STALACTITE_COUNT; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double edge = this.edgeRadius(angle);
				double distance = Math.sqrt(random.nextDouble()) * (edge - 6.0D);
				double x = this.centerX + 0.5D + Math.cos(angle) * distance;
				double z = this.centerZ + 0.5D + Math.sin(angle) * distance;
				double top = this.bottomAt(x - 0.5D, z - 0.5D) + 2.0D;
				double inner = 1.0D - distance / edge;
				double length = 8.0D + random.nextDouble() * 10.0D + inner * (MAX_STALACTITE - 18);
				double radius = 2.0D + random.nextDouble() * 2.0D + inner * 2.0D;
				this.stalactites.add(Cone.of(x, top, z, x + (random.nextDouble() - 0.5D) * 3.0D, top - length, z + (random.nextDouble() - 0.5D) * 3.0D,
						radius, UNDER));
			}
		}

		private double radiusAt(double x, double z) {
			double dx = x - this.centerX;
			double dz = z - this.centerZ;
			return Math.sqrt(dx * dx + dz * dz);
		}

		private double bottomAt(double x, double z) {
			double dx = x - this.centerX;
			double dz = z - this.centerZ;
			double r = Math.sqrt(dx * dx + dz * dz);
			double edge = this.edgeRadius(Math.atan2(dz, dx));
			double ratio = Math.min(1.0D, r / edge);
			double depth = UNDER_MIN + UNDER_DEPTH * (1.0D - Math.pow(ratio, 1.5D));
			return this.surfaceY - depth - noise(this.seed + 11L, x / 9.0D, z / 9.0D) * 6.0D;
		}

		private int topAt(int x, int z, double r, double edge) {
			int top = this.surfaceY;
			if (r <= DAIS_RADIUS + 0.5D) return top + 1;
			double bump = noise(this.seed + 3L, x / 7.0D, z / 7.0D) * 2.2D;
			int peak = Math.floorMod(hash(this.seed, x, z), 100) < 5 ? 1 + Math.floorMod(hash(this.seed + 1L, x, z), 3) : 0;
			if (r > edge - 4.0D) top += 1;
			return top + (int) Math.floor(bump) + peak;
		}

		public Region classify(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			Region region = new Region(minX, minY, minZ, maxX, maxY, maxZ);
			int cageTop = Math.min(this.maxY, CEILING);

			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					double dx = x + 0.5D - (this.centerX + 0.5D);
					double dz = z + 0.5D - (this.centerZ + 0.5D);
					double r = Math.sqrt(dx * dx + dz * dz);
					double angle = Math.atan2(dz, dx);
					double edge = this.edgeRadius(angle);
					if (r > REACH) continue;

					if (r <= edge) {
						int top = this.topAt(x, z, r, edge);
						int bottom = (int) Math.floor(this.bottomAt(x, z));
						double soil = noise(this.seed + 5L, x / 5.0D, z / 5.0D) + (Math.floorMod(hash(this.seed, x, z), 100) - 50) / 400.0D;
						boolean magma = Math.floorMod(hash(this.seed + 7L, x, z), 1000) < 22;
						for (int y = Math.max(minY, bottom); y <= Math.min(maxY, top); y++) {
							byte code;
							if (y == top) code = magma ? MAGMA : (soil > 0.58D ? TOPSOIL : ROCK);
							else if (y < bottom + 3) code = Math.floorMod(hash(this.seed + 9L, x, y * 31 + z), 100) < 6 ? MAGMA : UNDER;
							else code = ROCK;
							region.set(x, y, z, code);
						}
						for (int y = Math.max(minY, top + 1); y <= Math.min(maxY, cageTop); y++) {
							boolean light = y == top + 6 && Math.floorMod(x, LIGHT_SPACING) == 0 && Math.floorMod(z, LIGHT_SPACING) == 0;
							region.set(x, y, z, light ? LIGHT : AIR);
						}
					} else if (r <= edge + 30.0D) {
						for (int y = Math.max(minY, this.surfaceY - 4); y <= Math.min(maxY, cageTop); y++) region.set(x, y, z, AIR);
					}
				}
			}

			this.rasterizeCones(region, this.stalactites);
			this.rasterizeCones(region, this.spikes);
			this.rasterizeHorns(region);
			this.buildThrone(region);
			return region;
		}

		private void rasterizeCones(Region region, List<Cone> cones) {
			for (Cone cone : cones) {
				int x0 = Math.max(cone.minX(), region.minX);
				int x1 = Math.min(cone.maxX(), region.maxX());
				int y0 = Math.max(cone.minY(), region.minY);
				int y1 = Math.min(cone.maxY(), region.maxY());
				int z0 = Math.max(cone.minZ(), region.minZ);
				int z1 = Math.min(cone.maxZ(), region.maxZ());
				if (x0 > x1 || y0 > y1 || z0 > z1) continue;

				double ax = cone.tipX() - cone.baseX();
				double ay = cone.tipY() - cone.baseY();
				double az = cone.tipZ() - cone.baseZ();
				double length2 = ax * ax + ay * ay + az * az;
				for (int x = x0; x <= x1; x++) {
					for (int y = y0; y <= y1; y++) {
						for (int z = z0; z <= z1; z++) {
							double px = x + 0.5D - cone.baseX();
							double py = y + 0.5D - cone.baseY();
							double pz = z + 0.5D - cone.baseZ();
							double t = (px * ax + py * ay + pz * az) / length2;
							if (t < 0.0D || t > 1.0D) continue;
							double qx = px - ax * t;
							double qy = py - ay * t;
							double qz = pz - az * t;
							double allowed = cone.radius() * (1.0D - t) + 0.35D;
							if (qx * qx + qy * qy + qz * qz > allowed * allowed) continue;
							byte code = cone.code();
							if (code == HORN && t > 0.82D) code = GLOW;
							region.raise(x, y, z, code);
						}
					}
				}
			}
		}

		private void rasterizeHorns(Region region) {
			for (Horn horn : this.horns) {
				if (horn.maxX() < region.minX || horn.minX() > region.maxX() || horn.maxZ() < region.minZ || horn.minZ() > region.maxZ()
						|| horn.maxY() < region.minY || horn.minY() > region.maxY()) continue;

				for (int s = 0; s < HORN_SAMPLES; s++) {
					double ax = horn.xs()[s], ay = horn.ys()[s], az = horn.zs()[s];
					double bx = horn.xs()[s + 1], by = horn.ys()[s + 1], bz = horn.zs()[s + 1];
					double ra = horn.radii()[s], rb = horn.radii()[s + 1];
					double pad = Math.max(ra, rb) + 1.0D;
					int x0 = Math.max((int) Math.floor(Math.min(ax, bx) - pad), region.minX);
					int x1 = Math.min((int) Math.ceil(Math.max(ax, bx) + pad), region.maxX());
					int y0 = Math.max((int) Math.floor(Math.min(ay, by) - pad), region.minY);
					int y1 = Math.min((int) Math.ceil(Math.max(ay, by) + pad), region.maxY());
					int z0 = Math.max((int) Math.floor(Math.min(az, bz) - pad), region.minZ);
					int z1 = Math.min((int) Math.ceil(Math.max(az, bz) + pad), region.maxZ());
					if (x0 > x1 || y0 > y1 || z0 > z1) continue;

					double sx = bx - ax, sy = by - ay, sz = bz - az;
					double length2 = Math.max(1.0E-6D, sx * sx + sy * sy + sz * sz);
					for (int x = x0; x <= x1; x++) {
						for (int y = y0; y <= y1; y++) {
							for (int z = z0; z <= z1; z++) {
								double px = x + 0.5D - ax, py = y + 0.5D - ay, pz = z + 0.5D - az;
								double t = Math.max(0.0D, Math.min(1.0D, (px * sx + py * sy + pz * sz) / length2));
								double qx = px - sx * t, qy = py - sy * t, qz = pz - sz * t;
								double distance = Math.sqrt(qx * qx + qy * qy + qz * qz);
								double radius = ra + (rb - ra) * t;
								if (distance > radius) continue;
								boolean shell = distance > radius - GLOW_SHELL;
								double facing = distance < 1.0E-4D ? 0.0D : (qx * horn.outX() + qz * horn.outZ()) / distance;
								region.raise(x, y, z, shell && facing > GLOW_FACING ? GLOW : HORN);
							}
						}
					}
				}
			}
		}

		private void buildThrone(Region region) {
			int top = this.surfaceY + 1;
			for (int dx = -DAIS_RADIUS; dx <= DAIS_RADIUS; dx++) {
				for (int dz = -DAIS_RADIUS; dz <= DAIS_RADIUS; dz++) {
					double r = Math.sqrt(dx * dx + dz * dz);
					if (r > DAIS_RADIUS + 0.3D) continue;
					int x = this.centerX + dx;
					int z = this.centerZ + dz;
					region.set(x, top, z, r > DAIS_RADIUS - 1.0D ? GLOW : BRICK);
					region.set(x, top - 1, z, BRICK);
				}
			}
			for (int dx = -3; dx <= 3; dx++) {
				int height = THRONE_HEIGHT - Math.abs(dx);
				for (int h = 1; h <= height; h++) {
					region.set(this.centerX + dx, top + h, this.centerZ - THRONE_BACK, h == height ? GLOW : BRICK);
					region.set(this.centerX + dx, top + h, this.centerZ - THRONE_BACK - 1, BRICK);
				}
			}
			for (int side = -1; side <= 1; side += 2) {
				int x = this.centerX + side * 4;
				for (int h = 1; h <= 3; h++) region.set(x, top + h, this.centerZ - THRONE_BACK + 1, BRICK);
				for (int h = THRONE_HEIGHT - 2; h <= THRONE_HEIGHT + 3; h++) {
					region.set(this.centerX + side * (3 + (h - THRONE_HEIGHT + 2) / 2), top + h, this.centerZ - THRONE_BACK, h >= THRONE_HEIGHT + 1 ? GLOW : HORN);
				}
			}
		}

		private static int hash(long seed, int x, int z) {
			long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
			h ^= h >>> 33;
			h *= 0xFF51AFD7ED558CCDL;
			h ^= h >>> 33;
			return (int) h;
		}

		private static double lattice(long seed, int x, int z) {
			return (hash(seed, x, z) & 0xFFFF) / 65535.0D;
		}

		private static double noise(long seed, double x, double z) {
			int x0 = (int) Math.floor(x);
			int z0 = (int) Math.floor(z);
			double fx = x - x0;
			double fz = z - z0;
			double u = fx * fx * (3.0D - 2.0D * fx);
			double v = fz * fz * (3.0D - 2.0D * fz);
			double a = lattice(seed, x0, z0);
			double b = lattice(seed, x0 + 1, z0);
			double c = lattice(seed, x0, z0 + 1);
			double d = lattice(seed, x0 + 1, z0 + 1);
			return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
		}
	}
}
