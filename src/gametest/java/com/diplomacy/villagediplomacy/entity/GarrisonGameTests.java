package com.diplomacy.villagediplomacy.entity;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSpecialization;
import com.diplomacy.villagediplomacy.garrison.GarrisonSavedData;
import com.diplomacy.villagediplomacy.garrison.GarrisonService;
import com.diplomacy.villagediplomacy.garrison.ResidentData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(VillageDiplomacyMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarrisonGameTests {
    @GameTest(template = "guard_arena", batch = "recruitment")
    public static void trainingAndDemobilizationPreserveTheActualPerson(GameTestHelper helper) {
        Fixture f = fixture(helper, 8);
        Villager recruit = f.residents[0];
        recruit.setCustomName(Component.literal("Test Resident"));
        recruit.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
        recruit.setVillagerXp(11);
        UUID identity = recruit.getUUID();
        SimpleContainer kit = kit();
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, recruit, kit).equals("started"), "Recruitment must start");
        helper.assertTrue(kit.isEmpty(), "Exact equipment and food must be reserved");
        helper.assertTrue(GarrisonService.population(helper.getLevel(), f.home).trainees() == 1, "Trainee must occupy an army place");
        helper.assertFalse(GarrisonService.finishTraining(helper.getLevel(), recruit), "Training must not finish early");
        ResidentData.data(recruit).getCompound(ResidentData.TRAINING).putLong("Finish", helper.getLevel().getGameTime());
        helper.assertTrue(GarrisonService.finishTraining(helper.getLevel(), recruit), "Training must produce a real guard");
        var record = GarrisonSavedData.get(helper.getLevel()).get(identity);
        VillageGuardEntity guard = (VillageGuardEntity) helper.getLevel().getEntity(record.entity());
        helper.assertTrue(recruit.isRemoved(), "Original civilian must be removed, not duplicated");
        helper.assertTrue(guard.getResidentIdentity().orElseThrow().equals(identity), "Guard must retain civilian identity");
        helper.assertTrue(guard.getCustomName().getString().equals("Test Resident"), "Name must survive conversion");
        helper.assertTrue(guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "Use the supplied armor, not free chainmail");
        helper.assertTrue(guard.getUniformSpecialization() == SettlementSpecialization.FOREST, "Uniform must follow the home");
        guard.setHealth(15);
        CompoundTag guardSave = guard.saveWithoutId(new CompoundTag());
        guard.discard();
        guard = ModEntities.VILLAGE_GUARD.get().create(helper.getLevel());
        guard.load(guardSave);
        helper.getLevel().addFreshEntity(guard);
        helper.assertTrue(guard.getResidentIdentity().orElseThrow().equals(identity), "Guard origin must survive its own NBT reload");
        helper.assertTrue(GarrisonService.demobilize(f.leader, f.home, guard).equals("demobilized"), "Demobilization must succeed");
        Villager restored = (Villager) helper.getLevel().getEntity(identity);
        helper.assertTrue(restored != null && guard.isRemoved(), "Exactly one civilian must return");
        helper.assertTrue(restored.getVillagerXp() == 11 && restored.getInventory().getItem(0).is(Items.APPLE)
                && restored.getInventory().getItem(0).getCount() == 3, "XP and original inventory must be preserved");
        helper.assertTrue(Math.abs(restored.getHealth() - 10) < .01, "Demobilization must preserve health ratio");
        helper.assertTrue(f.leader.getInventory().countItem(Items.IRON_SWORD) == 1
                && f.leader.getInventory().countItem(Items.LEATHER_CHESTPLATE) == 1, "Actual equipment must return once");
        helper.assertTrue(GarrisonService.population(helper.getLevel(), f.home).civilians() == 8, "Population must be restored");
        helper.succeed();
    }

    @GameTest(template = "guard_arena", batch = "recruitment")
    public static void deathDoesNotRecreateAResidentOrArmyPlace(GameTestHelper helper) {
        Fixture f = fixture(helper, 5);
        Villager recruit = f.residents[0];
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, recruit, kit()).equals("started"), "Start trainee");
        recruit.kill();
        GarrisonService.register(helper.getLevel(), recruit);
        helper.assertTrue(GarrisonService.population(helper.getLevel(), f.home).trainees() == 0
                && GarrisonService.population(helper.getLevel(), f.home).civilians() == 4,
                "A dead trainee must not be counted again while its corpse is still loaded");
        helper.assertFalse(GarrisonService.finishTraining(helper.getLevel(), recruit), "A dead person cannot finish training");
        helper.succeed();
    }

    @GameTest(template = "guard_arena", batch = "recruitment")
    public static void permissionsChildrenProfessionsAndSuppliesAreValidated(GameTestHelper helper) {
        Fixture f = fixture(helper, 8);
        ServerPlayer outsider = player(helper);
        helper.assertTrue(GarrisonService.recruit(outsider, f.home, f.residents[0], kit()).equals("not_leader"), "Only the actual leader may recruit");
        f.residents[0].setBaby(true);
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, f.residents[0], kit()).equals("ineligible"), "Never recruit children");
        f.residents[0].setBaby(false);
        f.residents[0].setVillagerData(f.residents[0].getVillagerData().setProfession(VillagerProfession.FARMER));
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, f.residents[0], kit()).equals("ineligible"), "Keep employed villagers at work");
        f.residents[0].setVillagerData(f.residents[0].getVillagerData().setProfession(VillagerProfession.NONE));
        SimpleContainer incomplete = new SimpleContainer(3);
        incomplete.setItem(0, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, f.residents[0], incomplete).equals("supplies"), "Require complete supply kit");
        helper.assertTrue(incomplete.getItem(0).getCount() == 1 && !ResidentData.isTraining(f.residents[0]), "Refused action must not consume anything");
        helper.succeed();
    }

    @GameTest(template = "guard_arena", batch = "recruitment")
    public static void queueCannotExceedPopulationLimitAndCancellationRefundsOnce(GameTestHelper helper) {
        Fixture f = fixture(helper, 8);
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, f.residents[0], kit()).equals("started"), "First place");
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, f.residents[1], kit()).equals("started"), "Second place");
        SimpleContainer third = kit();
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, f.residents[2], third).equals("population_limit"), "Queued trainees count toward 25 percent limit");
        helper.assertTrue(third.getItem(2).getCount() == 6, "Refused third recruit must not consume food");
        helper.assertTrue(GarrisonService.cancel(f.leader, f.home, f.residents[0]).equals("cancelled"), "Cancel training");
        helper.assertTrue(GarrisonService.cancel(f.leader, f.home, f.residents[0]).equals("ineligible"), "Cannot refund twice");
        helper.assertTrue(f.leader.getInventory().countItem(Items.BREAD) == 6, "Refund exact reserved food once");
        helper.assertTrue(GarrisonService.population(helper.getLevel(), f.home).trainees() == 1, "Cancellation releases one army place");
        helper.succeed();
    }

    @GameTest(template = "guard_arena", batch = "recruitment")
    public static void trainingEscrowAndIdentitySurviveEntityReload(GameTestHelper helper) {
        Fixture f = fixture(helper, 5);
        Villager recruit = f.residents[0];
        helper.assertTrue(GarrisonService.recruit(f.leader, f.home, recruit, kit()).equals("started"), "Start before save");
        UUID id = recruit.getUUID();
        long remaining = ResidentData.remaining(recruit);
        CompoundTag saved = recruit.saveWithoutId(new CompoundTag());
        recruit.discard();
        Villager loaded = EntityType.VILLAGER.create(helper.getLevel()); loaded.load(saved); helper.getLevel().addFreshEntity(loaded);
        helper.assertTrue(loaded.getUUID().equals(id) && ResidentData.isTraining(loaded)
                && ResidentData.remaining(loaded) == remaining, "Identity, escrow and finish time must survive NBT reload");
        helper.assertTrue(GarrisonService.population(helper.getLevel(), f.home).trainees() == 1, "Reload must not duplicate the queued place");
        SettlementSavedData.get(helper.getLevel()).removeSettlement(f.home.getId());
        helper.assertFalse(GarrisonService.finishTraining(helper.getLevel(), loaded), "Deleted home must cancel training instead of spawning an orphan guard");
        helper.assertFalse(ResidentData.isTraining(loaded), "Cancellation must also work before the training deadline");
        helper.succeed();
    }

    private static SimpleContainer kit() {
        return new SimpleContainer(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(Items.BREAD, 6));
    }
    private record Fixture(Settlement home, ServerPlayer leader, Villager[] residents) { }
    private static Fixture fixture(GameTestHelper helper, int count) {
        ServerPlayer leader = player(helper);
        Settlement home = new Settlement(UUID.randomUUID(), "Recruitment test", helper.absolutePos(new BlockPos(5, 2, 5)), 7);
        home.setLeaderUuid(leader.getUUID()); home.setSpecializations(SettlementSpecialization.FOREST, null);
        SettlementSavedData.get(helper.getLevel()).registerSettlement(home);
        Villager[] residents = new Villager[count];
        for (int i = 0; i < count; i++) {
            residents[i] = helper.spawn(EntityType.VILLAGER, 3 + i % 4, 2, 3 + i / 4);
            residents[i].setNoAi(true);
            ResidentData.data(residents[i]).putUUID("Home", home.getId());
        }
        GarrisonService.discover(helper.getLevel());
        return new Fixture(home, leader, residents);
    }
    private static ServerPlayer player(GameTestHelper helper) {
        // The vanilla helper installs a connection without a Netty channel;
        // Forge's login pipeline needs its supported server-only FakePlayer.
        return net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "RecruitmentTest"));
    }
}
