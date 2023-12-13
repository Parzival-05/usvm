package org.usvm.machine

import org.usvm.domain.GoInst
import org.usvm.targets.UTarget

abstract class GoTarget(
    location: GoInst,
) : UTarget<GoInst, GoTarget>(location)