package net.jgpower.gichan_land.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmergencyPresenceBottomLine(
    rescueStatus: String,
    policeStatus: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        HorizontalDivider()

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmergencyPresenceMiniItem(
                label = "구조대",
                status = rescueStatus,
                modifier = Modifier.weight(1f)
            )

            EmergencyPresenceMiniItem(
                label = "경찰",
                status = policeStatus,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmergencyPresenceMiniItem(
    label: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label 재실 여부",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = " / ",
            fontSize = 12.sp
        )

        Text(
            text = status,
            fontSize = 12.sp
        )
    }
}