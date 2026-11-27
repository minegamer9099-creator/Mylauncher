# MyLauncher release (R8) rules.
# Manifest ke components (MainActivity, SettingsActivity, MediaListener ...) AGP khud keep karta hai.
# Neeche sirf warnings dabane ke liye hain.
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**

# Zyada aggressive shrink: chhota + tez code
-allowaccessmodification
-repackageclasses ''
