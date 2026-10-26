# Archived sealed-cargo prototype

These sources were moved out of the active Gradle source set when Arthur's
`test` branch (`41653db`) was integrated into **our `2nd-test` branch**.
They preserve the earlier sealed-crate item, mount attachment, warehouse and
route prototype for reference. They are not a standalone, buildable module.

The active game uses ordinary Minecraft stacks and the existing
`TradeContractService` / `TradeHandoverService`. These prototype classes must not
be registered alongside that implementation without an explicit adaptation.

The complete pre-integration state, including the original settlement and
contract classes, remains at `codex/backup-2nd-test-20261025` (`1b59c3d`).
Neither the friend's local `test` branch nor its remote branch was modified.

Existing worlds made with the crate prototype have a different settlement,
warehouse and contract NBT schema. No automatic migration is provided in this
integration. Back up those worlds before opening them with the integrated mod;
guard verification uses only `build/guard-gametest-run`.
