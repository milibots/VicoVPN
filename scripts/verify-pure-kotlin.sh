#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/Smoke.kt" <<'EOFKT'
import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.parser.ShareLinkParser
import com.vicovpn.client.util.JsonCodec
import com.vicovpn.client.util.Redactor
import com.vicovpn.client.xray.XrayConfigBuilder
import java.util.Base64

private fun expectFailure(label: String, block: () -> Unit) {
    check(runCatching(block).isFailure) { "$label should fail" }
}

fun main() {
    val uuid = "11111111-1111-4111-8111-111111111111"
    val vmessJson = JsonCodec.stringify(mapOf(
        "v" to "2", "ps" to "VMess", "add" to "example.com", "port" to "443",
        "id" to uuid, "aid" to "0", "net" to "ws", "path" to "/ray",
        "host" to "cdn.example.com", "tls" to "tls", "sni" to "example.com"
    ))
    val vmessUri = "vmess://" + Base64.getUrlEncoder().withoutPadding().encodeToString(vmessJson.toByteArray())
    val vmess = ShareLinkParser.parse(vmessUri)
    check(vmess is ProxyProfile.Vmess && vmess.port == 443)

    check(ShareLinkParser.parse(
        "vless://$uuid@example.com:443?encryption=none&security=reality&type=tcp&sni=example.com&pbk=public-key&sid=ab#Reality"
    ) is ProxyProfile.Vless)
    check(ShareLinkParser.parse(
        "trojan://secret@example.com:443?security=tls&type=grpc&serviceName=svc#Trojan"
    ) is ProxyProfile.Trojan)
    check(ShareLinkParser.parse(
        "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@example.com:8388#SS"
    ) is ProxyProfile.Shadowsocks)

    expectFailure("unsupported transport") {
        ShareLinkParser.parse("vless://$uuid@example.com:443?type=kcp")
    }
    expectFailure("Shadowsocks plugin") {
        ShareLinkParser.parse("ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@example.com:8388?plugin=v2ray-plugin#SS")
    }

    val config = XrayConfigBuilder.build(vmess, 45123)
    val root = JsonCodec.parseObject(config)
    val inbounds = root["inbounds"] as List<*>
    check(((inbounds[1] as Map<*, *>)["port"] as Number).toInt() == 45123)
    check(config.contains("\"protocol\":\"tun\""))
    check(config.contains("\"outboundTag\":\"proxy\""))
    check(!config.contains("allowInsecure"))

    val redacted = Redactor.redact("vless://$uuid@example.com:443")
    check(!redacted.contains(uuid) && !redacted.contains("vless://"))
    println("PURE_KOTLIN_SMOKE_OK profiles=4 configBytes=${config.length}")
}
EOFKT
kotlinc \
  "$ROOT/app/src/main/java/com/vicovpn/client/util/JsonCodec.kt" \
  "$ROOT/app/src/main/java/com/vicovpn/client/util/Redactor.kt" \
  "$ROOT/app/src/main/java/com/vicovpn/client/model/ProxyProfile.kt" \
  "$ROOT/app/src/main/java/com/vicovpn/client/parser/ShareLinkParser.kt" \
  "$ROOT/app/src/main/java/com/vicovpn/client/xray/XrayConfigBuilder.kt" \
  "$TMP/Smoke.kt" -include-runtime -d "$TMP/smoke.jar"
java -jar "$TMP/smoke.jar"
