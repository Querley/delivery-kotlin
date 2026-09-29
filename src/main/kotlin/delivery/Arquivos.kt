package delivery

import com.google.gson.GsonBuilder
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*

class Arquivos(val pasta: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    companion object {
        private val travaLocal = Any()
        val CABECALHO = listOf(
            "id_pedido", "data_hora", "email_restaurante", "nome_restaurante", "telefone_cliente",
            "nome_cliente", "endereco_cliente", "numero_item", "quantidade", "descricao_item",
            "valor_unitario", "valor_total_item", "status"
        )
    }

    // As duas aplicações usam a mesma trava durante a leitura e gravação.
    fun <T> transacao(acao: Arquivos.() -> T): T = synchronized(travaLocal) {
        Files.createDirectories(pasta)
        RandomAccessFile(pasta.resolve(".delivery.lock").toFile(), "rw").use { arquivo ->
            arquivo.channel.lock().use { acao(this) }
        }
    }

    internal fun restaurantes(): List<Pair<Path, Restaurante>> {
        return Files.list(pasta).use { caminhos ->
            caminhos.filter { Regex("restaurante_[0-9]+\\.json").matches(it.fileName.toString()) }
                .sorted().map { caminho ->
                    val restaurante = gson.fromJson(Files.readString(caminho), Restaurante::class.java)
                    requireNotNull(restaurante) { "Arquivo vazio: ${caminho.fileName}" }
                    requireNotNull(restaurante.nome) { "Restaurante sem nome." }
                    requireNotNull(restaurante.email) { "Restaurante sem e-mail." }
                    requireNotNull(restaurante.endereco) { "Restaurante sem endereço." }
                    requireNotNull(restaurante.menu) { "Restaurante sem cardápio." }
                    restaurante.menu.forEach {
                        requireNotNull(it.descricao) { "Item sem descrição." }
                        validarPreco(requireNotNull(it.preco) { "Item sem preço." })
                        require(it.numeroItem > 0) { "Número de item inválido." }
                    }
                    caminho to restaurante
                }.toList()
        }
    }

    internal fun clientes(): List<Cliente> {
        val arquivo = pasta.resolve("clientes.json")
        if (!Files.exists(arquivo)) return emptyList()
        val lista = gson.fromJson(Files.readString(arquivo), Array<Cliente>::class.java)
        requireNotNull(lista) { "clientes.json está vazio ou inválido." }
        lista.forEach {
            requireNotNull(it.nome) { "Cliente sem nome." }
            requireNotNull(it.telefone) { "Cliente sem telefone." }
            requireNotNull(it.endereco) { "Cliente sem endereço." }
        }
        return lista.toList()
    }

    internal fun salvarClientes(lista: List<Cliente>) = gravar(pasta.resolve("clientes.json"), gson.toJson(lista) + "\n")
    internal fun salvarRestaurante(caminho: Path, restaurante: Restaurante) = gravar(caminho, gson.toJson(restaurante) + "\n")

    internal fun novoArquivoRestaurante(): Path {
        val maior = restaurantes().maxOfOrNull { (arquivo, _) ->
            arquivo.fileName.toString().removePrefix("restaurante_").removeSuffix(".json").toInt()
        } ?: 0
        return pasta.resolve("restaurante_${maior + 1}.json")
    }

    internal fun pedidos(): List<LinhaPedido> {
        val arquivo = pasta.resolve("pedidos.csv")
        if (!Files.exists(arquivo)) return emptyList()
        val formato = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).build()
        Files.newBufferedReader(arquivo).use { reader ->
            formato.parse(reader).use { csv ->
                require(csv.headerNames == CABECALHO) { "Cabeçalho de pedidos.csv inválido. O arquivo não foi alterado." }
                return csv.map { linha ->
                    require(linha.size() == CABECALHO.size) { "Linha inválida em pedidos.csv." }
                    LinhaPedido(
                        linha["id_pedido"], linha["data_hora"], linha["email_restaurante"], linha["nome_restaurante"],
                        linha["telefone_cliente"], linha["nome_cliente"], linha["endereco_cliente"],
                        linha["numero_item"].toInt(), linha["quantidade"].toInt(), linha["descricao_item"],
                        linha["valor_unitario"].toBigDecimal(), linha["valor_total_item"].toBigDecimal(), linha["status"].toInt()
                    ).also { require(it.status in 0..4) { "Status inválido no arquivo de pedidos." } }
                }
            }
        }
    }

    internal fun salvarPedidos(lista: List<LinhaPedido>) {
        val texto = StringBuilder()
        val formato = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(*CABECALHO.toTypedArray()).build()
        CSVPrinter(texto, formato).use { csv ->
            lista.forEach { linha ->
                csv.printRecord(
                    linha.idPedido, linha.dataHora, linha.emailRestaurante, linha.nomeRestaurante,
                    linha.telefoneCliente, linha.nomeCliente, linha.enderecoCliente, linha.numeroItem,
                    linha.quantidade, linha.descricaoItem, linha.valorUnitario.toPlainString(),
                    linha.valorTotalItem.toPlainString(), linha.status
                )
            }
        }
        gravar(pasta.resolve("pedidos.csv"), texto.toString())
    }

    private fun gravar(destino: Path, texto: String) {
        val temporario = Files.createTempFile(pasta, ".delivery-", ".tmp")
        try {
            Files.writeString(temporario, texto)
            try {
                Files.move(temporario, destino, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporario, destino, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporario)
        }
    }
}
