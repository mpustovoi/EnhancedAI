package insane96mcp.enhancedai.modules.base.feature;

import insane96mcp.enhancedai.modules.base.ai.EASpiderTargetGoal;
import insane96mcp.enhancedai.setup.Config;
import insane96mcp.insanelib.ai.ILNearestAttackableTargetGoal;
import insane96mcp.insanelib.base.Feature;
import insane96mcp.insanelib.base.Label;
import insane96mcp.insanelib.base.Module;
import insane96mcp.insanelib.config.Blacklist;
import insane96mcp.insanelib.util.MCUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Label(name = "Targeting", description = "Change how mobs target players")
public class Targeting extends Feature {

	private final ForgeConfigSpec.ConfigValue<Integer> followRangeConfig;
	private final ForgeConfigSpec.ConfigValue<Double> swimSpeedMultiplierConfig;
	private final ForgeConfigSpec.ConfigValue<Double> xrayConfig;
	private final ForgeConfigSpec.ConfigValue<Boolean> instaTargetConfig;
	private final ForgeConfigSpec.BooleanValue betterPathfindingConfig;
	private final Blacklist.Config entityBlacklistConfig;

	private final List<String> entityBlacklistDefault = Arrays.asList("minecraft:enderman");

	public int followRange = 48;
	public double swimSpeedMultiplier = 2.5d;
	public double xray = 0.20d;
	public boolean instaTarget = true;
	public boolean betterPathfinding = true;
	public Blacklist entityBlacklist;

	public Targeting(Module module) {
		super(Config.builder, module);
		this.pushConfig(Config.builder);
		followRangeConfig = Config.builder
				.comment("How far away can the mobs see the player. This overrides the vanilla value (16 for most mobs). Setting to 0 will leave the follow range as vanilla. I recommend using mods like Mobs Properties Randomness to have more control over the attribute.")
				.defineInRange("Follow Range Override", this.followRange, 0, 128);
		swimSpeedMultiplierConfig = Config.builder
				.comment("How faster mobs can swim. Setting to 0 will leave the swim speed as vanilla. I recommend using mods like Mobs Properties Randomness to have more control over the attribute.")
				.defineInRange("Swim Speed Multiplier", this.swimSpeedMultiplier, 0d, 4d);
		xrayConfig = Config.builder
				.comment("Chance for a mob to be able to see players through blocks.")
				.defineInRange("XRay Chance", xray, 0d, 1d);
		instaTargetConfig = Config.builder
				.comment("Mobs will no longer take random time to target a player.")
				.define("Instant Target", instaTarget);
		betterPathfindingConfig = Config.builder
				.comment("Mobs will be able to find better paths to the target. Note that this might hit performance a bit.")
				.define("Better Path Finding", this.betterPathfinding);
		entityBlacklistConfig = new Blacklist.Config(Config.builder, "Entity Blacklist", "Entities in here will not have the TargetAI changed")
				.setDefaultList(entityBlacklistDefault)
				.setIsDefaultWhitelist(false)
				.build();
		Config.builder.pop();
	}

	@Override
	public void loadConfig() {
		super.loadConfig();
		this.followRange = this.followRangeConfig.get();
		this.swimSpeedMultiplier = this.swimSpeedMultiplierConfig.get();
		this.xray = this.xrayConfig.get();
		this.instaTarget = this.instaTargetConfig.get();
		this.betterPathfinding = this.betterPathfindingConfig.get();
		this.entityBlacklist = this.entityBlacklistConfig.get();
	}

	final UUID UUID_SWIM_SPEED_MULTIPLIER = UUID.fromString("6d2cb27e-e5e3-41b9-8108-f74131a90cce");

	//High priority as should run before specific mobs
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onMobSpawn(EntityJoinWorldEvent event) {
		if (!this.isEnabled())
			return;

		if (!(event.getEntity() instanceof Mob mobEntity))
			return;

		if (this.entityBlacklist.isEntityBlackOrNotWhitelist(mobEntity))
			return;

		if (this.followRange != 0 && mobEntity.getAttribute(Attributes.FOLLOW_RANGE) != null && mobEntity.getAttribute(Attributes.FOLLOW_RANGE).getBaseValue() < this.followRange) {
			MCUtils.setAttributeValue(mobEntity, Attributes.FOLLOW_RANGE, this.followRange);
		}

		if (this.swimSpeedMultiplier != 0d) {
			MCUtils.applyModifier(mobEntity, ForgeMod.SWIM_SPEED.get(), UUID_SWIM_SPEED_MULTIPLIER, "Enhanced AI Swim Speed Multiplier", this.swimSpeedMultiplier, AttributeModifier.Operation.MULTIPLY_BASE, false);
		}

		boolean hasTargetGoal = false;

		Predicate<LivingEntity> predicate = null;

		ArrayList<Goal> goalsToRemove = new ArrayList<>();
		for (WrappedGoal prioritizedGoal : mobEntity.targetSelector.availableGoals) {
			if (!(prioritizedGoal.getGoal() instanceof NearestAttackableTargetGoal<?> goal))
				continue;

			if (goal.targetType != Player.class)
				continue;

			predicate = goal.targetConditions.selector;

			goalsToRemove.add(prioritizedGoal.getGoal());
			hasTargetGoal = true;
		}

		if (!hasTargetGoal)
			return;

		goalsToRemove.forEach(mobEntity.targetSelector::removeGoal);

		ILNearestAttackableTargetGoal<Player> targetGoal;

		if (mobEntity instanceof Spider)
			targetGoal = new EASpiderTargetGoal<>((Spider) mobEntity, Player.class, true, false, predicate);
		else
			targetGoal = new ILNearestAttackableTargetGoal<>(mobEntity, Player.class, false, false, predicate);
		if (mobEntity.level.random.nextDouble() < this.xray)
			targetGoal.setIgnoreLineOfSight();

		if (this.instaTarget)
			targetGoal.setInstaTarget();
		mobEntity.targetSelector.addGoal(2, targetGoal);
		if (this.betterPathfinding)
			mobEntity.getNavigation().setMaxVisitedNodesMultiplier(4f);

		ILNearestAttackableTargetGoal<Endermite> targetGoalTest;

		if (mobEntity instanceof Spider)
			targetGoalTest = new EASpiderTargetGoal<>((Spider) mobEntity, Endermite.class, true, false, predicate);
		else
			targetGoalTest = new ILNearestAttackableTargetGoal<>(mobEntity, Endermite.class, false, false, predicate);
		if (mobEntity.level.random.nextDouble() < this.xray)
			targetGoalTest.setIgnoreLineOfSight();

		mobEntity.targetSelector.addGoal(2, targetGoalTest.setInstaTarget());
	}
}
