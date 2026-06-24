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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.datastore.PendingLogoutStore
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.AuthRepository
import net.jgpower.gichan_land.util.ErrorMessageSanitizer

@Composable
fun SignInScreen(
    onLoginSuccess: (String) -> Unit
) {
    val workerId = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val appContext = context.applicationContext

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
                                val inputWorkerId = workerId.value.trim()
                                val inputPassword = password.value.trim()

                                // 네트워크가 끊겼다가 다시 연결된 경우 기존 Retrofit URL/인스턴스가
                                // 현재 네트워크 상태와 다를 수 있어 로그인 직전에 다시 초기화합니다.
                                ApiServiceManager.init(appContext)

                                var authRepository = AuthRepository(ApiServiceManager.apiService)

                                // 이전 로그아웃 시 네트워크가 끊겨 서버 logout이 실패했다면
                                // 같은 workerId 로그인 전에 서버 상태를 먼저 정리합니다.
                                val pendingLogoutWorkerId = PendingLogoutStore.get(appContext)
                                if (pendingLogoutWorkerId == inputWorkerId) {
                                    try {
                                        authRepository.logout(inputWorkerId)
                                        PendingLogoutStore.clear(appContext)
                                        delay(300L)
                                    } catch (_: Exception) {
                                        // 아직 서버에 연결되지 않으면 아래 로그인에서 정상 오류를 표시합니다.
                                    }
                                }

                                var response = authRepository.login(
                                    workerId = inputWorkerId,
                                    password = inputPassword
                                )

                                // 서버에는 아직 로그인 중으로 남아 있지만 실제 앱/네트워크는 끊긴 stale 상태일 수 있습니다.
                                // 같은 ID로 로그인 재시도 시 409가 나오면 1회만 logout 후 재로그인을 시도합니다.
                                if (!response.isSuccessful && response.code() == 409) {
                                    try {
                                        authRepository.logout(inputWorkerId)
                                        PendingLogoutStore.clearIfMatches(appContext, inputWorkerId)
                                        delay(300L)
                                        ApiServiceManager.init(appContext)
                                        authRepository = AuthRepository(ApiServiceManager.apiService)
                                        response = authRepository.login(
                                            workerId = inputWorkerId,
                                            password = inputPassword
                                        )
                                    } catch (_: Exception) {
                                        // 재정리 실패 시 기존 409 처리로 내려갑니다.
                                    }
                                }

                                if (response.isSuccessful) {
                                    val body = response.body()

                                    if (body?.success == true && body.worker != null) {
                                        PendingLogoutStore.clearIfMatches(appContext, body.worker.workerId)
                                        onLoginSuccess(body.worker.workerId)
                                    } else {
                                        errorMessage.value =
                                            ErrorMessageSanitizer.genericNetworkError(body?.message, "로그인에 실패했습니다.")
                                    }
                                } else {
                                    errorMessage.value = when (response.code()) {
                                        409 -> "이미 로그인 된 계정입니다. 잠시 후 다시 시도하거나 관리자에게 로그인 상태 초기화를 요청하세요."
                                        401 -> "직원 ID 또는 비밀번호가 올바르지 않습니다."
                                        else -> "서버 오류가 발생했습니다. (${response.code()})"
                                    }
                                }
                            } catch (_: Exception) {
                                errorMessage.value =
                                    "서버에 연결할 수 없습니다. 네트워크 연결 후 다시 시도하세요."
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