package com.maku.idleharvest.fakes

/**
 * Base package for fake implementations used in property-based and unit tests.
 *
 * Fake implementations provide controlled, deterministic behavior for testing
 * domain logic without real platform dependencies (BLE, network, filesystem, etc.).
 *
 * Conventions:
 * - Prefix fake class names with "Fake" (e.g., FakeResourceMonitor, FakePolicyManager)
 * - Fakes should implement the corresponding interface from domain/interfaces
 * - Fakes expose internal state for assertion purposes
 * - Fakes should be stateful where needed to simulate realistic interaction sequences
 */
