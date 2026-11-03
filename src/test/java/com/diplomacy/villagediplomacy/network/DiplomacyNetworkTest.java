package com.diplomacy.villagediplomacy.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiplomacyNetworkTest {
    @Test
    void actionIdsSurviveTransportWithoutByteTruncation() {
        for (int action : new int[]{1_000, 1_127, 2_000, 3_279}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var message = new DiplomacyNetwork.MenuAction(100, action);
                message.write(buffer);
                assertEquals(message, DiplomacyNetwork.MenuAction.read(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }
}
