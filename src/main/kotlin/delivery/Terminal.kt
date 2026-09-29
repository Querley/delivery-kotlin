package delivery

import com.google.gson.JsonParseException
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.math.BigDecimal
import java.nio.file.Path

class FimDaEntrada : RuntimeException()

class Terminal(
    private val entrada: BufferedReader = (System.console()?.reader() ?: System.`in`.reader(Charsets.UTF_8)).buffered(),
    private val saida: PrintWriter = System.console()?.writer() ?: PrintWriter(System.out, true, Charsets.UTF_8)
) {
    fun mostrar(texto: String = "") { saida.println(texto) }

    fun ler(pergunta: String): String {
        saida.print(pergunta)
        saida.flush()
        return entrada.readLine()?.trim() ?: throw FimDaEntrada()
    }

    fun texto(pergunta: String): String {
        while (true) {
            val valor = ler(pergunta)
            if (valor.isNotBlank()) return valor
            mostrar("Este campo é obrigatório.")
        }
    }

    fun inteiro(pergunta: String, intervalo: IntRange = 1..Int.MAX_VALUE): Int {
        while (true) {
            val numero = ler(pergunta).toIntOrNull()
            if (numero != null && numero in intervalo) return numero
            mostrar("Informe um número inteiro entre ${intervalo.first} e ${intervalo.last}.")
        }
    }

    fun numeroOuFim(pergunta: String): Int? {
        while (true) {
            val texto = ler(pergunta)
            if (texto.isEmpty()) return null
            val numero = texto.toIntOrNull()
            if (numero != null && numero > 0) return numero
            mostrar("Informe um número positivo ou pressione Enter para terminar.")
        }
    }

    fun preco(): BigDecimal {
        while (true) {
            val texto = ler("Preço: ").replace(',', '.')
            val valor = texto.toBigDecimalOrNull()
            if (valor != null) {
                try { return validarPreco(valor) }
                catch (e: IllegalArgumentException) { mostrar(e.message.orEmpty()) }
            } else mostrar("Preço inválido. Exemplo: 45,00.")
        }
    }

    fun confirmar(): Boolean {
        while (true) {
            when (ler("Confirmar pedido [S/N]? ").uppercase()) {
                "S" -> return true
                "N" -> return false
                else -> mostrar("Digite S ou N.")
            }
        }
    }

    fun tentar(acao: () -> Unit) {
        try { acao() }
        catch (e: IllegalArgumentException) { mostrar("Erro: ${e.message}") }
        catch (e: JsonParseException) { mostrar("Erro ao ler JSON. Confira os arquivos de dados; nenhum cadastro foi substituído.") }
        catch (e: IOException) { mostrar("Erro ao acessar os arquivos: ${e.message}") }
    }

    fun cardapio(restaurante: Restaurante) {
        mostrar("\nCARDÁPIO - ${restaurante.nome}")
        if (restaurante.menu.isEmpty()) mostrar("Nenhum item cadastrado.")
        restaurante.menu.sortedBy { it.numeroItem }.forEach {
            mostrar("${it.numeroItem} - ${it.descricao} | ${moeda(it.preco)}")
        }
    }

    fun pedidos(linhas: List<LinhaPedido>) {
        if (linhas.isEmpty()) { mostrar("Nenhum pedido encontrado."); return }
        linhas.groupBy { it.idPedido }.forEach { (id, itens) ->
            val pedido = itens.first()
            mostrar("\nPedido: $id | ${pedido.dataHora}")
            mostrar("Restaurante: ${pedido.nomeRestaurante} | ${pedido.emailRestaurante}")
            mostrar("Cliente: ${pedido.nomeCliente} | ${pedido.telefoneCliente}")
            mostrar("Endereço: ${pedido.enderecoCliente}")
            mostrar("Status: ${pedido.status} - ${STATUS[pedido.status]}")
            itens.forEach { mostrar("${it.quantidade} x ${it.descricaoItem} | ${moeda(it.valorTotalItem)}") }
            mostrar("Total: ${moeda(itens.fold(BigDecimal.ZERO) { total, item -> total + item.valorTotalItem })}")
        }
    }
}

fun iniciar(args: Array<String>, terminal: Terminal, app: (Delivery) -> Unit) {
    try {
        terminal.tentar {
            require(args.isEmpty() || (args.size == 2 && args[0] == "--dados" && args[1].isNotBlank())) {
                "Uso: --dados caminho-da-pasta (opcional)."
            }
            val pasta = Path.of(if (args.isEmpty()) "dados" else args[1]).toAbsolutePath().normalize()
            terminal.mostrar("Pasta de dados: $pasta")
            app(Delivery(pasta))
        }
    } catch (_: FimDaEntrada) {
        terminal.mostrar("\nEntrada encerrada. Até logo!")
    }
}
