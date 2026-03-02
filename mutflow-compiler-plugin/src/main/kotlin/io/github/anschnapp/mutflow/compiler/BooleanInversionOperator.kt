package io.github.anschnapp.mutflow.compiler

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

/**
 * Mutation operator for boolean inversion: !expr ↔ expr
 *
 * Two cases:
 *
 * Case A — Remove negation:
 * - Matches `Boolean.not()` calls that are NOT from `!=` (EXCLEQ origin)
 * - Variant: unwrap the not(), returning the inner boolean expression
 *
 * Case B — Add negation:
 * - Matches boolean-returning calls with null origin (plain function calls)
 * - Excludes `not()` itself (already handled by Case A)
 * - Variant: wrap in `Boolean.not()`
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class BooleanInversionOperator : MutationOperator {

    override fun matches(call: IrCall): Boolean {
        val name = call.symbol.owner.name.asString()

        // Case A: not() call that isn't from != operator
        if (name == "not" && call.origin != IrStatementOrigin.EXCLEQ) {
            return true
        }

        // Case B: boolean-returning call with null origin (leaf boolean function call)
        if (call.type.isBoolean() && call.origin == null && name != "not") {
            return true
        }

        return false
    }

    override fun originalDescription(call: IrCall): String {
        val name = call.symbol.owner.name.asString()
        return if (name == "not" && call.origin != IrStatementOrigin.EXCLEQ) {
            "!"
        } else {
            "${name}()"
        }
    }

    override fun variants(call: IrCall, context: MutationContext): List<MutationOperator.Variant> {
        val name = call.symbol.owner.name.asString()

        // Case A: remove negation — !expr → expr
        if (name == "not" && call.origin != IrStatementOrigin.EXCLEQ) {
            return listOf(
                MutationOperator.Variant("removed !") {
                    call.dispatchReceiver!!.deepCopyWithSymbols()
                }
            )
        }

        // Case B: add negation — expr → !expr
        if (call.type.isBoolean() && call.origin == null && name != "not") {
            return listOf(
                MutationOperator.Variant("!${name}()") {
                    val booleanNotSymbol = context.pluginContext.irBuiltIns.booleanNotSymbol
                    context.builder.irCall(booleanNotSymbol).also {
                        it.dispatchReceiver = call.deepCopyWithSymbols()
                    }
                }
            )
        }

        return emptyList()
    }
}
