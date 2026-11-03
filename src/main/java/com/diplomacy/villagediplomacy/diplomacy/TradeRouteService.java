package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public final class TradeRouteService {
    private TradeRouteService() {
    }

    public static Result act(SettlementSavedData data, UUID homeId, UUID targetId, UUID playerId,
                             Action action, String name) {
        Settlement home = data.getSettlement(homeId);
        Settlement target = data.getSettlement(targetId);
        if (home == null || target == null || homeId.equals(targetId)) {
            return Result.UNAVAILABLE;
        }
        if (!home.isPlayerLeader(playerId)) {
            return Result.NOT_LEADER;
        }
        if (!home.getKnownSettlementIds().contains(targetId) && !TradeAgreementService.isAllied(home, target)) {
            return Result.UNDISCOVERED;
        }
        TradeRoute route = data.findTradeRoute(homeId, targetId);
        if (action == Action.CREATE) {
            if (route != null && !route.closed()) {
                return Result.UNAVAILABLE;
            }
        } else if (route == null || route.closed()) {
            return Result.UNAVAILABLE;
        }
        try {
            String routeName = action == Action.RENAME || action == Action.CREATE ? TradeRoute.cleanName(name) : route.name();
            data.putTradeRoute(new TradeRoute(route == null ? UUID.randomUUID() : route.id(),
                    route == null ? homeId : route.sourceId(), route == null ? targetId : route.targetId(),
                    routeName, action == Action.CLOSE));
            if (action == Action.CREATE) {
                data.discoverSettlement(targetId, homeId);
            }
            return Result.UPDATED;
        } catch (IllegalArgumentException exception) {
            return Result.INVALID_NAME;
        }
    }

    public static Result bindSignpost(SettlementSavedData data, UUID homeId, UUID targetId,
                                      BlockPos pos, UUID playerId, UUID ownerId) {
        Settlement home = data.getSettlement(homeId);
        Settlement target = data.getSettlement(targetId);
        if (home == null || target == null || homeId.equals(targetId)) {
            return Result.UNAVAILABLE;
        }
        if (!playerId.equals(ownerId) && !home.isPlayerLeader(playerId)) {
            return Result.NOT_LEADER;
        }
        if (!home.getKnownSettlementIds().contains(targetId) && !TradeAgreementService.isAllied(home, target)) {
            return Result.UNDISCOVERED;
        }
        data.putSignpost(pos, new SignpostLink(homeId, targetId));
        return Result.UPDATED;
    }

    public static boolean isMarked(SettlementSavedData data, UUID firstId, UUID secondId) {
        TradeRoute route = data.findTradeRoute(firstId, secondId);
        Settlement first = data.getSettlement(firstId);
        Settlement second = data.getSettlement(secondId);
        return route != null && !route.closed() && first != null && second != null
                && hasEndpointSign(data, first, secondId) && hasEndpointSign(data, second, firstId);
    }

    private static boolean hasEndpointSign(SettlementSavedData data, Settlement home, UUID targetId) {
        return data.getSignposts().entrySet().stream().anyMatch(entry ->
                entry.getValue().homeId().equals(home.getId()) && entry.getValue().targetId().equals(targetId)
                        && nearEndpoint(home, entry.getKey()));
    }

    public static boolean nearEndpoint(Settlement home, BlockPos pos) {
        double radius = home.getRadius() + 32.0D;
        return home.getCenter().distSqr(pos) <= radius * radius;
    }

    public static String bearing(BlockPos from, BlockPos to) {
        double degrees = Math.toDegrees(Math.atan2(to.getX() - (double) from.getX(), from.getZ() - (double) to.getZ()));
        return new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}[
                Math.floorMod((int) Math.round(degrees / 45.0D), 8)];
    }

    public record SignpostLink(UUID homeId, UUID targetId) {
    }

    public enum Action {
        CREATE, RENAME, CLOSE
    }

    public enum Result {
        UPDATED, NOT_LEADER, UNAVAILABLE, UNDISCOVERED, INVALID_NAME, MAP_GIVEN, INVENTORY_FULL
    }
}
