# TidyTroupe support guide

This guide describes the intended workflows; check the implementation and release notes for current availability. Production support contact is not configured.

## Getting started

Create a household with an adult administrator. Choose nicknames for family members. Add a chore, choose its due date, recurrence, assignee and optional stars. On shared tablets, return to the child experience and lock the parent area before handing over the device.

## Chores and approvals

A repeating chore has distinct dated occurrences. If approval is required, reporting completion requests adult review; it should not award stars immediately. An adult approves the occurrence once. If a completion was entered by mistake, use undo and check the resulting star balance. Pause a repeating chore when it is temporarily unnecessary.

## Voice does not start

Some Fire tablets do not include a compatible Android speech recognition service. Check microphone permission, installed language and network state. Use typed commands or touch controls when recognition is unavailable. TTS voices are separate from recognition languages. No always-listening mode or guaranteed offline recognition is promised.

## Reminders are late

Check notification permission, reminder opt-in, quiet hours and system battery restrictions. WorkManager reminders are inexact; the operating system can delay them. Do not use chore reminders for time-critical safety tasks.

## Data and devices

Core household data stays on this device. Cross-device sync is not configured. Exported backups may contain nicknames, notes and household activity; keep them private. Before import or deletion, confirm you selected the intended household and retain a usable backup when available.

## Purchases

Premium is planned as a one-time entitlement. Any live purchase must show the Amazon-provided local price and be accessible only in the adult area. A test price in planning documents is not an active store offer. Purchase/restore availability depends on the final Amazon integration and account configuration.

## Reporting a problem

Before public release, configure a support email or form. Reports should include app version, device model/Fire OS, selected language and reproduction steps. Ask users to remove household names and personal data from screenshots. Never request a parent PIN, purchase password or raw child audio.
