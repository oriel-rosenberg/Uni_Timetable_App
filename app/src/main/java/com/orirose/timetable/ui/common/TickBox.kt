package com.orirose.timetable.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orirose.timetable.R

private val BoxShape = RoundedCornerShape(7.dp)

/** The "done" tick for a lesson: a generous touch target around a small box in the course's colour. */
@Composable
fun TickBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(if (checked) accent else Color.Transparent, label = "tickFill")
    val outline = if (checked) accent else MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.mark_done)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(BoxShape)
                .background(fill)
                .border(1.75.dp, outline, BoxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = if (accent.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
