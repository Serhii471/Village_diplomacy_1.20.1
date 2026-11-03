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
        boolean hasAgreement = home.getRelationships().stream().anyMatch(relation ->
                relation.getTradeAgreementStatus() == TradeAgreementStatus.ACTIVE);
        int availableSlots = Math.max(0, offerRules.maxOpenOffers()
                * (hasAgreement ? DiplomacyBalance.AGREEMENT_OFFER_MULTIPLIER : 1) - liveOffers);
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
            if (target == null) {
                continue;
            }
            boolean agreement = TradeAgreementService.isActive(home, target);
            int pairLimit = agreement ? DiplomacyBalance.AGREEMENT_OFFER_MULTIPLIER : 1;
            long liveBetween = data.getAllTradeContracts().stream()
                    .filter(contract -> !contract.getStatus().isTerminal())
                    .filter(contract -> contract.involves(home.getId()) && contract.involves(targetId)).count();
            for (long slot = liveBetween; slot < pairLimit && generated.size() < availableSlots; slot++) {
                Optional<TradeContract> offer = createOffer(data, home, target, gameTime, offerRules, contractRules);
                if (offer.isEmpty()) {
                    break;
                }
                generated.add(offer.orElseThrow());
            }
        }
        return List.copyOf(generated);
    }

    private static Optional<TradeContract> createOffer(SettlementSavedData data,
                                                       Settlement home,
                                                       Settlement target,
                                                       long gameTime,
                                                       OfferRules offerRules,
                                                       TradeContractService.ContractRules contractRules) {
        boolean agreement = TradeAgreementService.isActive(home, target);
        int maximum = offerRules.maxItemAmount() * (agreement ? DiplomacyBalance.AGREEMENT_OFFER_MULTIPLIER : 1);
        Optional<GoodsChoice> outbound = bestSurplus(home, target, null, maximum);
        Optional<GoodsChoice> returning = bestSurplus(
                target,
                home,
                outbound.map(GoodsChoice::itemId).orElse(null),
                maximum
        );

        if (outbound.isPresent() && returning.isPresent()) {
            GoodsChoice sent = outbound.orElseThrow();
            GoodsChoice returned = returning.orElseThrow();
            int reward = reward(home, target, sent.amount() + returned.amount(), offerRules);
            if (agreement) {
                return Optional.of(TradeContractService.createAgreementOffer(data, home.getId(), target.getId(),
                        Map.of(sent.itemId(), sent.amount()), Map.of(returned.itemId(), returned.amount()),
                        reward, gameTime, contractRules));
            }
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
            if (agreement) {
                return Optional.of(TradeContractService.createAgreementOffer(data, home.getId(), target.getId(),
                        Map.of(sent.itemId(), sent.amount()), Map.of(),
                        reward(home, target, sent.amount(), offerRules), gameTime, contractRules));
            }
            return Optional.of(TradeContractService.createTrialDeliveryOffer(
                    data, home.getId(), target.getId(), Map.of(sent.itemId(), sent.amount()),
                    reward(home, target, sent.amount(), offerRules), gameTime, contractRules));
        }

        Optional<GoodsChoice> reverse = bestSurplus(target, home, null, maximum);
        if (reverse.isEmpty()) {
            return Optional.empty();
        }
        GoodsChoice sent = reverse.orElseThrow();
        if (agreement) {
            return Optional.of(TradeContractService.createAgreementOffer(data, target.getId(), home.getId(),
                    Map.of(sent.itemId(), sent.amount()), Map.of(),
                    reward(home, target, sent.amount(), offerRules), gameTime, contractRules));
        }
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
                .filter(entry -> entry.getValue().available() > receiver.getWarehouse().getStock(entry.getKey()).available())
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

    private static int reward(Settlement first,
                              Settlement second,
                              int totalItems,
                              OfferRules rules) {
        long distance = Math.round(Math.sqrt(first.getCenter().distSqr(second.getCenter())));
        long calculated = (long) rules.baseRewardEmeralds()
                + (distance + 255L) / 256L
                + Math.max(0, totalItems - 1) / 16L;
        int bonus = TradeAgreementService.isActive(first, second)
                ? DiplomacyBalance.AGREEMENT_REWARD_BONUS_EMERALDS : 0;
        return (int) Math.min((long) rules.maxRewardEmeralds() + bonus, calculated + bonus);
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
