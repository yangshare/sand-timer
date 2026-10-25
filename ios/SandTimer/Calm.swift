import SwiftUI

/// The clean view: while the sand runs, everything but the glass fades away after a few seconds — the
/// controls, the tabs and the status bar — and comes back when the screen is tapped. Bumping or picking
/// the phone up leaves it alone: a timer sitting on the desk shouldn't be woken by a nudge.
@MainActor
final class Calm: ObservableObject {
    static let shared = Calm()
    /// Everything but the glass is hidden.
    @Published private(set) var hidden = false
    private var watching = false
    private var fade: DispatchWorkItem?
    private static let hideAfter = 3.0

    /// Watches the phone while `on`: the sand running, with the clean view turned on and the timer showing.
    func watch(_ on: Bool) {
        guard on != watching else { return }
        watching = on
        if on {
            wake()
        } else {
            fade?.cancel()
            hidden = false
        }
    }

    /// The screen was tapped: show everything, and start counting the seconds again.
    func wake() {
        if hidden { hidden = false }
        fade?.cancel()
        guard watching else { return }
        let work = DispatchWorkItem { [weak self] in
            MainActor.assumeIsolated { if self?.watching == true { self?.hidden = true } }
        }
        fade = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.hideAfter, execute: work)
    }
}
