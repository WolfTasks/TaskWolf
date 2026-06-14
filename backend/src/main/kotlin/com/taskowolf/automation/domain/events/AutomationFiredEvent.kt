package com.taskowolf.automation.domain.events

import com.taskowolf.automation.domain.AutomationRule
import com.taskowolf.issues.domain.Issue

data class AutomationFiredEvent(val rule: AutomationRule, val issue: Issue)
