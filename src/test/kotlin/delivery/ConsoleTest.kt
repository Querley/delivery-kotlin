package delivery

import delivery.cliente.executarCliente
import delivery.restaurante.executarRestaurante
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files

class ConsoleTest {
    @get:Rule val temporaria = TemporaryFolder()
    private val pasta get() = temporaria.root.toPath()

    private fun rodar(entrada: String, restaurante: Boolean = false): String {
        val saida = StringWriter()
        val terminal = Terminal(entrada.reader().buffered(), PrintWriter(saida, true))
        val args = arrayOf("--dados", pasta.toString())
        if (restaurante) executarRestaurante(args, terminal) else executarCliente(args, terminal)
        return saida.toString()
    }

    private fun preparar() {
        val sistema = Delivery(pasta)
        sistema.cadastrarRestaurante("Pizza", "pizza@exemplo.com", "Rua A", listOf(ItemCardapio(1, "Pizza", "45.00".toBigDecimal())))
        sistema.cadastrarCliente("Ana", "62999998888", "Rua B")
    }

    @Test fun cancelamentoEPedidoVazioNaoGravamCsv() {
        preparar()
        val cancelado = rodar("1\n62999998888\n1\n1\n1\n2\n\nN\n0\n0\n")
        assertTrue(cancelado.contains("Total: R$ 90,00"))
        assertTrue(cancelado.contains("Pedido cancelado"))
        val vazio = rodar("1\n62999998888\n1\n1\n\n0\n0\n")
        assertTrue(vazio.contains("Pedido vazio"))
        assertFalse(Files.exists(pasta.resolve("pedidos.csv")))
    }

    @Test fun recuperaEntradaInvalidaESomaItemRepetido() {
        preparar()
        val saida = rodar("1\n62999998888\n1\n1\nabc\n99\n1\n0\n1\n1\n2\n\nX\nS\n0\n0\n")
        assertTrue(saida.contains("Item não encontrado"))
        assertTrue(saida.contains("Digite S ou N"))
        assertTrue(saida.contains("Total: R$ 135,00"))
        assertEquals(3, Delivery(pasta).pedidosCliente("62999998888", false).single().quantidade)
    }

    @Test fun cadastroInicialEMenuDoRestauranteFuncionam() {
        val saida = rodar("2\nPizza\npizza@exemplo.com\nRua A\n1\nPizza\n45,00\n\n1\nB\n2\nSuco\n6,50\nA\nC\n1\n0\n0\n0\n", true)
        assertTrue(saida.contains("Restaurante cadastrado com sucesso"))
        assertTrue(saida.contains("Item adicionado"))
        assertTrue(saida.contains("Item removido"))
        assertEquals(2, Delivery(pasta).entrarRestaurante("pizza@exemplo.com")!!.menu.single().numeroItem)
    }

    @Test fun fimDaEntradaEncerraSemLoopESemCadastroParcial() {
        assertTrue(rodar("2\nAna\n").contains("Entrada encerrada"))
        assertFalse(Files.exists(pasta.resolve("clientes.json")))
        val vazia = rodar("2\nAna\n62999998888\nRua A\n1\n0\n0\n")
        assertTrue(vazia.contains("Nenhum restaurante cadastrado"))
    }
}
