package insane96mcp.enhancedai.modules.skeleton.ai;

import insane96mcp.enhancedai.modules.base.ai.AIAvoidEntityGoal;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;

import java.util.EnumSet;

public class AIRangedBowAttackGoal<T extends MonsterEntity & IRangedAttackMob> extends Goal {
	private final T entity;
	private final double moveSpeedAmp;
	private int attackCooldown;
	private final float maxAttackDistance;
	private int attackTime = -1;
	private int seeTime;
	private final boolean canStrafe;
	private boolean strafingClockwise;
	private boolean strafingBackwards;
	private int strafingTime = -1;

	public AIRangedBowAttackGoal(T mob, double moveSpeedAmpIn, int attackCooldownIn, float maxAttackDistanceIn, boolean canStrafe) {
		this.entity = mob;
		this.moveSpeedAmp = moveSpeedAmpIn;
		this.attackCooldown = attackCooldownIn;
		this.maxAttackDistance = maxAttackDistanceIn * maxAttackDistanceIn;
		this.canStrafe = canStrafe;
		this.setFlags(EnumSet.of(Goal.Flag.LOOK));
	}

	public void setAttackCooldown(int attackCooldownIn) {
		this.attackCooldown = attackCooldownIn;
	}

	/**
	 * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
	 * method as well.
	 */
	public boolean canUse() {
		return this.entity.getTarget() != null && this.isBowInMainhand();
	}

	protected boolean isBowInMainhand() {
		return this.entity.isHolding(item -> item instanceof BowItem);
	}

	/**
	 * Returns whether an in-progress EntityAIBase should continue executing
	 */
	public boolean canContinueToUse() {
		return this.canUse() && this.isBowInMainhand();
	}

	/**
	 * Execute a one shot task or start executing a continuous task
	 */
	public void start() {
		super.start();
		this.entity.setAggressive(true);
	}

	/**
	 * Reset the task's internal state. Called when this task is interrupted by another one
	 */
	public void stop() {
		super.stop();
		this.entity.setAggressive(false);
		this.seeTime = 0;
		this.attackTime = -1;
		this.entity.stopUsingItem();
	}

	/**
	 * Keep ticking a continuous task that has already been started
	 */
	public void tick() {
		LivingEntity livingentity = this.entity.getTarget();
		if (livingentity == null)
			return;

		double distanceFromTarget = this.entity.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
		boolean canSeeTarget = this.entity.getSensing().canSee(livingentity);
		boolean flag1 = this.seeTime > 0;
		if (canSeeTarget != flag1) {
			this.seeTime = 0;
		}

		if (canSeeTarget) {
			++this.seeTime;
		}
		else {
			--this.seeTime;
		}
		if (distanceFromTarget > (double)this.maxAttackDistance)
			this.entity.getNavigation().moveTo(livingentity, this.moveSpeedAmp);
		else {

			if (distanceFromTarget <= (double)this.maxAttackDistance && this.seeTime >= 20 && this.canStrafe()) {
				//this.entity.getNavigator().clearPath();
				++this.strafingTime;
			}
			else {
				this.strafingTime = -1;
			}

			if (this.strafingTime >= 20) {
				if ((double)this.entity.getRandom().nextFloat() < 0.3D) {
					this.strafingClockwise = !this.strafingClockwise;
				}

				if ((double)this.entity.getRandom().nextFloat() < 0.3D) {
					this.strafingBackwards = !this.strafingBackwards;
				}

				this.strafingTime = 0;
			}

			int i = this.entity.getTicksUsingItem();
			if (i > 12) {
				this.entity.getNavigation().stop();
				this.entity.lookAt(livingentity, 30.0F, 30.0F);
				this.entity.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
			}
			else if (this.strafingTime > -1 && this.canStrafe()) {
				if (distanceFromTarget > (double)(this.maxAttackDistance * 0.9F)) {
					this.strafingBackwards = false;
				}
				else if (distanceFromTarget < (double)(this.maxAttackDistance * 0.8F)) {
					this.strafingBackwards = true;
				}

				this.entity.getMoveControl().strafe(this.strafingBackwards ? -0.5F : 0.5F, this.strafingClockwise ? 0.5F : -0.5F);
			}
			else {
				this.entity.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
			}

			if (this.entity.isUsingItem()) {
				if (!canSeeTarget && this.seeTime < -60) {
					this.entity.stopUsingItem();
				}
				else if (canSeeTarget) {
					if (i >= 20) {
						this.entity.stopUsingItem();
						attackEntityWithRangedAttack(this.entity, livingentity, 20);
						this.attackTime = this.attackCooldown;
					}
				}
			}
			else if (--this.attackTime <= 0 && this.seeTime >= -60) {
				this.entity.startUsingItem(ProjectileHelper.getWeaponHoldingHand(this.entity, item -> item == Items.BOW));
			}
		}
	}

	private boolean canStrafe() {
		return this.canStrafe && this.entity.goalSelector.getRunningGoals().anyMatch(p -> p.getGoal() instanceof AIAvoidEntityGoal);
	}

	private void attackEntityWithRangedAttack(T entity, LivingEntity target, float distanceFactor) {
		ItemStack itemstack = entity.getProjectile(entity.getItemInHand(ProjectileHelper.getWeaponHoldingHand(entity, item -> item == Items.BOW)));
		double distance = entity.distanceTo(target);
		double distanceY = target.getY() - entity.getY();
		float f = distanceFactor / 20.0F;
		f = (f * f + f * 2.0F) / 3.0F;
		AbstractArrowEntity abstractarrowentity = ProjectileHelper.getMobArrow(entity, itemstack, f);
		if (entity.getMainHandItem().getItem() instanceof net.minecraft.item.BowItem)
			abstractarrowentity = ((net.minecraft.item.BowItem)entity.getMainHandItem().getItem()).customArrow(abstractarrowentity);
		double d0 = target.getX() - entity.getX();
		double d2 = target.getZ() - entity.getZ();
		double distanceXZ = MathHelper.sqrt(d0 * d0 + d2 * d2);
		double yPos = target.getY(0d);
		//if (distanceY <= 0d || distanceY > distanceXZ)
			yPos += target.getEyeHeight() * 0.5 + (distanceY / distanceXZ);
		double d1 = yPos - abstractarrowentity.getY();
		//EnhancedAI.LOGGER.info(yPos + " " + d1 + " " + distanceXZ + " " + distanceY + " " + this.strafingTime + " " + this.strafingBackwards);
		//abstractarrowentity.shoot(d0, d1 + distanceXZ * (double)0.2F, d2, 1.6F, (float)(14 - entity.level.getDifficulty().getId() * 4));
		abstractarrowentity.shoot(d0, d1 + distanceXZ * 0.2d, d2, f * 1.1f + ((float)distance / 32f) + (float)Math.max(distanceY / 48d, 0f), 0);
		//abstractarrowentity.setGlowing(true);
		entity.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (entity.getRandom().nextFloat() * 0.4F + 0.8F));
		entity.level.addFreshEntity(abstractarrowentity);
	}
}
