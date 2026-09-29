package delivery

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class ItemCardapio(
    @SerializedName("numero_item") val numeroItem: Int,
    val descricao: String,
    val preco: BigDecimal
)

data class Restaurante(val nome: String, val email: String, val endereco: String, val menu: List<ItemCardapio>)
data class Cliente(val nome: String, val telefone: String, val endereco: String)
data class ItemPedido(val numeroItem: Int, val quantidade: Int, val descricao: String, val valorUnitario: BigDecimal) {
    val total: BigDecimal get() = valorUnitario.multiply(quantidade.toBigDecimal()).setScale(2)
}

data class LinhaPedido(
    val idPedido: String,
    val dataHora: String,
    val emailRestaurante: String,
    val nomeRestaurante: String,
    val telefoneCliente: String,
    val nomeCliente: String,
    val enderecoCliente: String,
    val numeroItem: Int,
    val quantidade: Int,
    val descricaoItem: String,
    val valorUnitario: BigDecimal,
    val valorTotalItem: BigDecimal,
    val status: Int
)

val STATUS = listOf("SOLICITADO", "EM PREPARACAO", "AGUARDANDO ENTREGADOR", "EM TRANSITO", "ENTREGUE")

fun normalizarEmail(email: String): String {
    val limpo = email.trim().lowercase(Locale.ROOT)
    require(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(limpo)) { "E-mail inválido." }
    return limpo
}

fun normalizarTelefone(telefone: String): String {
    require(telefone.all { it.isDigit() || it in " +()-" }) { "Telefone inválido." }
    val digitos = telefone.filter { it in '0'..'9' }
    require(digitos.length in 8..15) { "Informe um telefone com DDD (8 a 15 dígitos)." }
    return digitos
}

fun validarPreco(preco: BigDecimal): BigDecimal {
    require(preco > BigDecimal.ZERO && preco <= BigDecimal("999999.99")) { "O preço deve estar entre 0,01 e 999999,99." }
    require(preco.stripTrailingZeros().scale() <= 2) { "Use no máximo duas casas decimais." }
    return preco.setScale(2, RoundingMode.UNNECESSARY)
}

fun moeda(valor: BigDecimal): String = "R$ " + valor.setScale(2).toPlainString().replace('.', ',')
