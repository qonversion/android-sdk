# FinishingStubFragment is constructed directly by NoCodesFragmentFactory but may also be
# referenced by AndroidX FragmentManager via reflection (Class.forName) if it is ever
# persisted to a saved Bundle. Keep the no-arg constructor so reflective instantiation
# never fails after R8 shrinking in release builds.
-keep class io.qonversion.nocodes.internal.screen.view.FinishingStubFragment {
    <init>();
}
