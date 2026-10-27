package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Creates bounded, server-authored trial offers from complementary warehouse surplus. */
public final class TradeOfferGenerator {
    private TradeOfferGenerator() {
    }

    public static List<TradeContract> ensureOffers(SettlementSavedData data,
                                                   UUID settlementId,
                                                   long gameTime) {
        return ensureOffers(data, settlementId, gameTime, OfferRules.defaults(),
                TradeContractService.currentRules());
    }

    static List<TradeContract> ensureOffers(SettlementSavedData data,
                                            UUID settlementId,
                                            long gameTime,
                                            OfferRules offerRules,
                                            TradeContractService.ContractRules contractRules) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(offerRules, "offerRules");
        Objects.requireNonNull(contractRules, "contractRules");
        Settlement home = data.getSettlement(settlementId);
        if (home == null) {
            throw new IllegalArgumentException("Unknown settlement: " + settlementId);
        }

        TradeContractService.expireDueContracts(data, gameTime, contractRules);
        int liveOffers = (int) data.getAllTradeContracts().stream()
                .filter(contract -> contract.involves(settlementId))
                .filter(contract -> !contract.getStatus().isTerminal())
                .count();
        int availableSlots = Math.max(0, offerRules.maxOpenOffers() - liveOffers);
        if (availableSlots == 0) {
            return List.of();
        }

        List<TradeContract> generated = new ArrayList<>();
        List<UUID> knownTargets = home.getRelationships().stream()
                .filter(SettlementRelationship::isKnown)
                .map(SettlementRelationship::getTargetSettlementId)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        for (UUID targetId : knownTargets) {
            if (generated.size() >= availableSlots) {
                break;
            }
            Settlement target = data.getSettlement(targetId);
            if (target == null || hasLiveContractBetween(data, home.getId(), targetId)) {
                continue;
            }
            createOffer(data, home, target, gameTime, offerRules, contractRules)
                    .ifPresent(generated::add);
        }
        return List.copyOf(generated);
    }

    private static Optional<TradeContract> createOffer(SettlementSavedData data,
                                                       Settlement home,
                                                       Settlement target,
                                                       long gameTime,
                                                       OfferRules offerRules,
                                                       TradeContractService.ContractRules contractRules) {
        Optional<GoodsChoice> outbound = bestSurplus(home, target, null, offerRules.maxItemAmount());
        Optional<GoodsChoice> returning = bestSurplus(
                target,
                home,
                outbound.map(GoodsChoice::itemId).orElse(null),
                offerRules.maxItemAmount()
        );

        if (outbound.isPresent() && returning.isPresent()) {
            GoodsChoice sent = outbound.orElseThrow();
            GoodsChoice returned = returning.orElseThrow();
            int reward = reward(home, target, sent.amount() + returned.amount(), offerRules);
            return Optional.of(TradeContractService.createTrialExchangeOffer(
                    data,
                    home.getId(),
                    target.getId(),
                    Map.of(sent.itemId(), sent.amount()),
                    Map.of(returned.itemId(), returned.amount()),
                    reward,
                    gameTime,
                    contractRules
            ));
        }

        if (outbound.isPresent()) {
            GoodsChoice sent = outbound.orElseThrow();
            return Optional.of(TradeContractService.createTrialDeliveryOffer(
                    data, home.getId(), target.getId(), Map.of(sent.itemId(), sent.amount()),
                    reward(home, target, sent.amount(), offerRules), gameTime, contractRules));
        }

        Optional<GoodsChoice> reverse = bestSurplus(target, home, null, offerRules.maxItemAmount());
        if (reverse.isEmpty()) {
            return Optional.empty();
        }
        GoodsChoice sent = reverse.orElseThrow();
        return Optional.of(TradeContractService.createTrialDeliveryOffer(
                data, target.getId(), home.getId(), Map.of(sent.itemId(), sent.amount()),
                reward(home, target, sent.amount(), offerRules), gameTime, contractRules));
    }

    private static Optional<GoodsChoice> bestSurplus(Settlement source,
                                                     Settlement receiver,
                                                     ResourceLocation excludedItem,
                                                     int maximumAmount) {
        return source.getWarehouse().getContents().entrySet().stream()
                .filter(entry -> entry.getValue().available() > 0)
                .filter(entry -> !entry.getKey().equals(excludedItem))
                .filter(entry -> receiver.getWarehouse().getStock(entry.getKey()).available() == 0)
                .sorted(Comparator
                        .<Map.Entry<ResourceLocation, VirtualWarehouse.StockView>>comparingInt(
                                entry -> entry.getValue().available())
                        .reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .findFirst()
                .map(entry -> new GoodsChoice(
                        entry.getKey(),
                        Math.min(maximumAmount, Math.max(1, entry.getValue().available() / 2))
                ));
    }

    private static boolean hasLiveContractBetween(SettlementSavedData data, UUID firstId, UUID secondId) {
        return data.getAllTradeContracts().stream()
                .filter(contract -> !contract.getStatus().isTerminal())
                .anyMatch(contract -> contract.involves(firstId) && contract.involves(secondId));
    }

    private static int reward(Settlement first,
                              Settlement second,
                              int totalItems,
                              OfferRules rules) {
        long distance = Math.round(Math.sqrt(first.getCenter().distSqr(second.getCenter())));
        long calculated = (long) rules.baseRewardEmeralds()
                + (distance + 255L) / 256L
                + Math.max(0, totalItems - 1) / 16L;
        return (int) Math.min(rules.maxRewardEmeralds(), calculated);
    }

    private record GoodsChoice(ResourceLocation itemId, int amount) {
    }

    record OfferRules(int maxOpenOffers,
                      int maxItemAmount,
                      int baseRewardEmeralds,
                      int maxRewardEmeralds) {
        OfferRules {
            if (maxOpenOffers <= 0 || maxItemAmount <= 0 || baseRewardEmeralds < 0
                    || maxRewardEmeralds < baseRewardEmeralds) {
                throw new IllegalArgumentException("Invalid trade offer rules");
            }
        }

        static OfferRules defaults() {
            return new OfferRules(
                    DiplomacyBalance.MAX_OPEN_TRIAL_OFFERS,
                    DiplomacyBalance.TRIAL_OFFER_MAX_ITEM_AMOUNT,
                    DiplomacyBalance.TRIAL_OFFER_BASE_REWARD_EMERALDS,
                    DiplomacyBalance.TRIAL_OFFER_MAX_REWARD_EMERALDS
            );
        }
    }
}
