package com.nadirkhoulali.ucs.economy;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountType;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyFailureReason;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class UbsClaimEconomyProvider implements ClaimEconomyProvider {
    public static final String ID = "ubs";
    private static final Set<String> MOD_IDS = Set.of("ultimatebankingsystem", "ubs");
    private static final String API_PROVIDER_CLASS = "net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider";
    private static final String API_CLASS = "net.austizz.ultimatebankingsystem.api.UltimateBankingApi";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ultimate Banking System";
    }

    @Override
    public boolean isAvailable() {
        Object api = api();
        if (api == null) {
            return false;
        }
        return invokeBoolean(api, "isServerAvailable");
    }

    @Override
    public ClaimEconomyResult balance(ClaimEconomyAccountRef account) {
        Object api = apiOrUnavailable();
        if (api instanceof ClaimEconomyResult result) {
            return result;
        }
        if (account.type() == ClaimEconomyAccountType.SERVER_LEDGER) {
            return unsupported(BigDecimal.ZERO, "UBS does not expose server ledger balances.");
        }
        if (account.type() == ClaimEconomyAccountType.PLAYER_PRIMARY) {
            return mapApiResult(invokeApi(api, "getPlayerPrimaryBalance", new Class<?>[]{UUID.class}, account.id()),
                    BigDecimal.ZERO);
        }
        return mapApiResult(invokeApi(api, "getBalance", new Class<?>[]{UUID.class}, account.id()), BigDecimal.ZERO);
    }

    @Override
    public ClaimEconomyResult validateCanCharge(ClaimEconomyAccountRef account, BigDecimal amount) {
        BigDecimal value = normalizeAmount(amount);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return invalidAmount(value);
        }
        Object api = apiOrUnavailable();
        if (api instanceof ClaimEconomyResult result) {
            return result;
        }
        Optional<UUID> accountId = resolveSpendAccountId(api, account);
        if (accountId.isEmpty()) {
            return ClaimEconomyResult.fail(
                    ClaimEconomyFailureReason.ACCOUNT_NOT_FOUND,
                    "Primary account not found.",
                    value,
                    format(value));
        }
        return mapApiResult(invokeApi(api,
                "validateAccountCanSend",
                new Class<?>[]{UUID.class, BigDecimal.class},
                accountId.get(),
                value), value);
    }

    @Override
    public ClaimEconomyResult charge(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
        BigDecimal value = normalizeAmount(amount);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return invalidAmount(value);
        }
        Object api = apiOrUnavailable();
        if (api instanceof ClaimEconomyResult result) {
            return result;
        }
        if (account.type() == ClaimEconomyAccountType.SERVER_LEDGER) {
            return unsupported(value, "UBS cannot charge a server ledger directly.");
        }
        if (account.type() == ClaimEconomyAccountType.PLAYER_PRIMARY) {
            return mapTransactionResult(invokeApi(api,
                    "withdrawFromPrimary",
                    new Class<?>[]{UUID.class, BigDecimal.class, String.class},
                    account.id(),
                    value,
                    normalizeReference(reference)), value);
        }
        return mapTransactionResult(invokeApi(api,
                "withdraw",
                new Class<?>[]{UUID.class, BigDecimal.class, String.class},
                account.id(),
                value,
                normalizeReference(reference)), value);
    }

    @Override
    public ClaimEconomyResult refund(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
        BigDecimal value = normalizeAmount(amount);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return invalidAmount(value);
        }
        Object api = apiOrUnavailable();
        if (api instanceof ClaimEconomyResult result) {
            return result;
        }
        if (account.type() == ClaimEconomyAccountType.SERVER_LEDGER) {
            return unsupported(value, "UBS cannot refund to a server ledger directly.");
        }
        if (account.type() == ClaimEconomyAccountType.PLAYER_PRIMARY) {
            return mapTransactionResult(invokeApi(api,
                    "depositToPrimary",
                    new Class<?>[]{UUID.class, BigDecimal.class, String.class},
                    account.id(),
                    value,
                    normalizeReference(reference)), value);
        }
        return mapTransactionResult(invokeApi(api,
                "deposit",
                new Class<?>[]{UUID.class, BigDecimal.class, String.class},
                account.id(),
                value,
                normalizeReference(reference)), value);
    }

    @Override
    public ClaimEconomyResult transfer(
            ClaimEconomyAccountRef sender,
            ClaimEconomyAccountRef receiver,
            BigDecimal amount,
            String reference) {
        BigDecimal value = normalizeAmount(amount);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return invalidAmount(value);
        }
        if (sender.type() == ClaimEconomyAccountType.SERVER_LEDGER) {
            return refund(receiver, value, reference);
        }
        if (receiver.type() == ClaimEconomyAccountType.SERVER_LEDGER) {
            return charge(sender, value, reference);
        }

        Object api = apiOrUnavailable();
        if (api instanceof ClaimEconomyResult result) {
            return result;
        }
        String normalizedReference = normalizeReference(reference);
        if (sender.type() == ClaimEconomyAccountType.PLAYER_PRIMARY) {
            Optional<UUID> receiverAccount = receiver.type() == ClaimEconomyAccountType.PLAYER_PRIMARY
                    ? resolvePrimaryAccountId(api, receiver.id())
                    : Optional.of(receiver.id());
            if (receiverAccount.isEmpty()) {
                return ClaimEconomyResult.fail(
                        ClaimEconomyFailureReason.ACCOUNT_NOT_FOUND,
                        "Receiver primary account not found.",
                        value,
                        format(value));
            }
            return mapTransactionResult(invokeApi(api,
                    "transferFromPrimary",
                    new Class<?>[]{UUID.class, UUID.class, BigDecimal.class, String.class},
                    sender.id(),
                    receiverAccount.get(),
                    value,
                    normalizedReference), value);
        }
        if (receiver.type() == ClaimEconomyAccountType.PLAYER_PRIMARY) {
            return mapTransactionResult(invokeApi(api,
                    "transferToPrimary",
                    new Class<?>[]{UUID.class, UUID.class, BigDecimal.class, String.class},
                    sender.id(),
                    receiver.id(),
                    value,
                    normalizedReference), value);
        }
        return mapTransactionResult(invokeApi(api,
                "transfer",
                new Class<?>[]{UUID.class, UUID.class, BigDecimal.class, String.class},
                sender.id(),
                receiver.id(),
                value,
                normalizedReference), value);
    }

    @Override
    public String format(BigDecimal amount) {
        Object api = api();
        BigDecimal value = normalizeAmount(amount);
        if (api == null) {
            return "$" + value.stripTrailingZeros().toPlainString();
        }
        Object formatted = invokeApi(api, "formatMoneyRounded", new Class<?>[]{BigDecimal.class}, value);
        return formatted instanceof String text && !text.isBlank()
                ? text
                : "$" + value.stripTrailingZeros().toPlainString();
    }

    private Object apiOrUnavailable() {
        Object api = api();
        return api == null || !invokeBoolean(api, "isServerAvailable")
                ? ClaimEconomyResult.fail(
                        ClaimEconomyFailureReason.PROVIDER_UNAVAILABLE,
                        "Ultimate Banking System is not available.",
                        BigDecimal.ZERO,
                        format(BigDecimal.ZERO))
                : api;
    }

    private Object api() {
        if (!isUbsLoaded()) {
            return null;
        }
        try {
            Class<?> providerClass = Class.forName(API_PROVIDER_CLASS);
            Method get = providerClass.getMethod("get");
            Object api = get.invoke(null);
            Class.forName(API_CLASS).cast(api);
            return api;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private boolean isUbsLoaded() {
        try {
            ModList modList = ModList.get();
            for (String modId : MOD_IDS) {
                if (modList.isLoaded(modId)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object invokeApi(Object api, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = Class.forName(API_CLASS).getMethod(methodName, parameterTypes);
            return method.invoke(api, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private boolean invokeBoolean(Object api, String methodName) {
        Object value = invokeApi(api, methodName, new Class<?>[0]);
        return value instanceof Boolean bool && bool;
    }

    private Optional<UUID> resolveSpendAccountId(Object api, ClaimEconomyAccountRef account) {
        if (account.type() == ClaimEconomyAccountType.PROVIDER_ACCOUNT) {
            return Optional.of(account.id());
        }
        if (account.type() == ClaimEconomyAccountType.PLAYER_PRIMARY) {
            return resolvePrimaryAccountId(api, account.id());
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<UUID> resolvePrimaryAccountId(Object api, UUID playerId) {
        Object value = invokeApi(api, "getPrimaryAccountId", new Class<?>[]{UUID.class}, playerId);
        return value instanceof Optional<?> optional ? (Optional<UUID>) optional : Optional.empty();
    }

    private ClaimEconomyResult mapApiResult(Object result, BigDecimal amount) {
        if (result == null) {
            return ClaimEconomyResult.fail(
                    ClaimEconomyFailureReason.OPERATION_FAILED,
                    "Economy operation failed.",
                    amount,
                    format(amount));
        }
        boolean success = readBoolean(result, "success");
        String reason = readString(result, "reason");
        BigDecimal balanceAfter = readBigDecimal(result, "balanceAfter");
        if (success) {
            return ClaimEconomyResult.ok(amount, balanceAfter, "", format(amount));
        }
        return ClaimEconomyResult.fail(mapFailure(reason), userSafeReason(reason), amount, format(amount));
    }

    private ClaimEconomyResult mapTransactionResult(Object result, BigDecimal amount) {
        if (result == null) {
            return ClaimEconomyResult.fail(
                    ClaimEconomyFailureReason.OPERATION_FAILED,
                    "Economy transaction failed.",
                    amount,
                    format(amount));
        }
        boolean success = readBoolean(result, "success");
        String reason = readString(result, "reason");
        BigDecimal transactionAmount = readBigDecimal(result, "amount");
        BigDecimal balanceAfter = readBigDecimal(result, "balanceAfter");
        String transactionId = readUuidString(result, "transactionId");
        if (success) {
            return ClaimEconomyResult.ok(transactionAmount, balanceAfter, transactionId, format(transactionAmount));
        }
        return ClaimEconomyResult.fail(mapFailure(reason), userSafeReason(reason), amount, format(amount));
    }

    private ClaimEconomyFailureReason mapFailure(String reason) {
        String value = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (value.contains("primary account not found") || value.contains("account not found")) {
            return ClaimEconomyFailureReason.ACCOUNT_NOT_FOUND;
        }
        if (value.contains("insufficient")) {
            return ClaimEconomyFailureReason.INSUFFICIENT_FUNDS;
        }
        if (value.contains("frozen") || value.contains("unavailable") || value.contains("access")) {
            return ClaimEconomyFailureReason.ACCOUNT_UNAVAILABLE;
        }
        if (value.contains("greater than zero") || value.contains("amount")) {
            return ClaimEconomyFailureReason.INVALID_AMOUNT;
        }
        if (value.contains("same account")) {
            return ClaimEconomyFailureReason.SAME_ACCOUNT;
        }
        return ClaimEconomyFailureReason.OPERATION_FAILED;
    }

    private String userSafeReason(String reason) {
        return reason == null || reason.isBlank() ? "Economy operation failed." : reason.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
    }

    private String normalizeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return "UCS";
        }
        String trimmed = reference.trim();
        return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
    }

    private ClaimEconomyResult invalidAmount(BigDecimal amount) {
        return ClaimEconomyResult.fail(
                ClaimEconomyFailureReason.INVALID_AMOUNT,
                "Amount must be greater than zero.",
                amount,
                format(amount));
    }

    private ClaimEconomyResult unsupported(BigDecimal amount, String message) {
        return ClaimEconomyResult.fail(ClaimEconomyFailureReason.UNSUPPORTED_OPERATION, message, amount, format(amount));
    }

    private boolean readBoolean(Object target, String accessor) {
        Object value = readValue(target, accessor);
        return value instanceof Boolean bool && bool;
    }

    private String readString(Object target, String accessor) {
        Object value = readValue(target, accessor);
        return value instanceof String text ? text : "";
    }

    private String readUuidString(Object target, String accessor) {
        Object value = readValue(target, accessor);
        return value instanceof UUID uuid ? uuid.toString() : "";
    }

    private BigDecimal readBigDecimal(Object target, String accessor) {
        Object value = readValue(target, accessor);
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.ZERO;
    }

    private Object readValue(Object target, String accessor) {
        try {
            Method method = target.getClass().getMethod(accessor);
            return method.invoke(target);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
