package com.fiveseven.tabataxtreme

import android.app.Application

abstract class BaseApp : Application() {
    abstract val entryGate: EntryGate
}
