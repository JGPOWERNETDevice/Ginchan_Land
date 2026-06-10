package net.jgpower.gichan_land.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.AuthRepository

@Composable
fun SignInScreen(
    onLoginSuccess: (String) -> Unit
) {
    val workerId = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val authRepository = remember {
        AuthRepository(ApiServiceManager.apiService)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "기찬랜드 현장 직원 앱",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "부여받은 직원 ID와 비밀번호로 로그인하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = workerId.value,
                    onValueChange = { workerId.value = it },
                    label = { Text("직원 ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading.value
                )

                OutlinedTextField(
                    value = password.value,
                    onValueChange = { password.value = it },
                    label = { Text("비밀번호") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading.value,
                    visualTransformation = PasswordVisualTransformation()
                )

                errorMessage.value?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        if (workerId.value.isBlank()) {
                            errorMessage.value = "직원 ID를 입력하세요."
                            return@Button
                        }

                        if (password.value.isBlank()) {
                            errorMessage.value = "비밀번호를 입력하세요."
                            return@Button
                        }

                        coroutineScope.launch {
                            isLoading.value = true
                            errorMessage.value = null

                            try {
                                val response = authRepository.login(
                                    workerId = workerId.value.trim(),
                                    password = password.value.trim()
                                )

                                if (response.isSuccessful) {
                                    val body = response.body()

                                    if (body?.success == true && body.worker != null) {
                                        onLoginSuccess(body.worker.workerId)
                                    } else {
                                        errorMessage.value =
                                            body?.message ?: "로그인에 실패했습니다."
                                    }
                                } else {
                                    errorMessage.value = when (response.code()) {
                                        409 -> "이미 로그인 된 계정입니다."
                                        401 -> "직원 ID 또는 비밀번호가 올바르지 않습니다."
                                        else -> "서버 오류가 발생했습니다. (${response.code()})"
                                    }
                                }
                            } catch (_: Exception) {
                                errorMessage.value =
                                    "서버에 연결할 수 없습니다. Node-RED 주소를 확인하세요."
                            } finally {
                                isLoading.value = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading.value
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator()
                    } else {
                        Text("로그인")
                    }
                }
            }
        }
    }
}