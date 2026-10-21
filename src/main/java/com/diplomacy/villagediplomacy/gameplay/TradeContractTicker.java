package com.diplomacy.villagediplomacy.gameplay;

import com.diplomacy.villagediplomacy.config.VillageDiplomacyConfig;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractService;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TradeContractTicker {
    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel serverLevel)) {
            return;
        }

        int interval = VillageDiplomacyConfig.SERVER.contractCheckIntervalTicks.get();
        if (serverLevel.getGameTime() % interval == 0L) {
            TradeContractService.expireDueContracts(
                    SettlementSavedData.get(serverLevel),
                    serverLevel.getGameTime()
            );
        }
    }
}
