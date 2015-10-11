package name.abuchen.portfolio.snapshot.security;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import name.abuchen.portfolio.math.Risk;
import name.abuchen.portfolio.model.Adaptable;
import name.abuchen.portfolio.model.Attributable;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.InvestmentVehicle;
import name.abuchen.portfolio.model.Named;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.MutableMoney;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ReportingPeriod;

public final class SecurityPerformanceRecord implements Adaptable
{
    public enum Periodicity
    {
        UNKNOWN, NONE, INDEFINITE, ANNUAL, SEMIANNUAL, QUARTERLY, IRREGULAR;

        private static final ResourceBundle RESOURCES = ResourceBundle
                        .getBundle("name.abuchen.portfolio.snapshot.labels"); //$NON-NLS-1$

        public String toString()
        {
            return RESOURCES.getString("dividends." + name()); //$NON-NLS-1$
        }
    }

    private final Security security;
    private List<Transaction> transactions = new ArrayList<Transaction>();

    /**
     * internal rate of return of security {@link #calculateIRR()}
     */
    private double irr;

    /**
     * True time-weighted rate of return
     * {@link #calculatePerformance(ReportingPeriod)}
     */
    private double twror;

    /**
     * Max Drawdown and Max Drawdown Duration
     * {@link #calculatePerformance(ReportingPeriod)}
     */
    private Risk.Drawdown drawdown;

    /**
     * Volatility and semi-volatility
     * {@link #calculatePerformance(ReportingPeriod)}
     */
    private Risk.Volatility volatility;

    /**
     * delta = market value + sells + dividends - purchase costs
     * {@link #calculateDelta()}
     */
    private Money delta;

    /**
     * market value of holdings at end of period
     * {@link #addTransaction(Transaction)}
     */
    private Money marketValue;

    /**
     * fifo cost of shares held {@link #calculateFifoCosts()}
     */
    private Money fifoCost;

    /**
     * fees paid
     */
    private Money fees;

    /**
     * taxes paid
     */
    private Money taxes;

    /**
     * shares held {@link #calculateFifoCosts()}
     */
    private long sharesHeld;

    /**
     * cost per shares held {@link #calculateFifoCosts()}
     */
    private Money fifoCostPerSharesHeld;

    /**
     * sum of all dividend payments {@link #calculateDividends()}
     */
    private Money sumOfDividends;

    /**
     * number of dividend events during reporting period
     * {@link #calculateDividends()}
     */
    private int dividendEventCount;

    /**
     * last dividend payment in reporting period {@link #calculateDividends()}
     */
    private LocalDate lastDividendPayment;

    /**
     * periodicity of dividend payments {@link #calculateDividends()}
     */
    private Periodicity periodicity = Periodicity.UNKNOWN;

    /* package */SecurityPerformanceRecord(Security security)
    {
        this.security = security;
    }

    public Security getSecurity()
    {
        return security;
    }

    public String getSecurityName()
    {
        return getSecurity().getName();
    }

    public String getNote()
    {
        return getSecurity().getNote();
    }

    public double getIrr()
    {
        return irr;
    }

    public double getTrueTimeWeightedRateOfReturn()
    {
        return twror;
    }

    public double getMaxDrawdown()
    {
        return drawdown.getMaxDrawdown();
    }

    public long getMaxDrawdownDuration()
    {
        return drawdown.getMaxDrawdownDuration().getDays();
    }

    public double getVolatility()
    {
        return volatility.getStandardDeviation();
    }

    public double getSemiVolatility()
    {
        return volatility.getSemiDeviation();
    }

    public Money getDelta()
    {
        return delta;
    }

    public Money getMarketValue()
    {
        return marketValue;
    }

    public Money getFifoCost()
    {
        return fifoCost;
    }

    public Money getFees()
    {
        return fees;
    }

    public Money getTaxes()
    {
        return taxes;
    }

    public long getSharesHeld()
    {
        return sharesHeld;
    }

    public Money getFifoCostPerSharesHeld()
    {
        return fifoCostPerSharesHeld;
    }

    public Money getSumOfDividends()
    {
        return sumOfDividends;
    }

    public int getDividendEventCount()
    {
        return dividendEventCount;
    }

    public LocalDate getLastDividendPayment()
    {
        return lastDividendPayment;
    }

    public Periodicity getPeriodicity()
    {
        return periodicity;
    }

    public int getPeriodicitySort()
    {
        return periodicity.ordinal();
    }

    public double getTotalRateOfReturnDiv()
    {
        return sharesHeld > 0 ? (double) sumOfDividends.getAmount() / (double) fifoCost.getAmount() : 0;
    }

    public List<Transaction> getTransactions()
    {
        return transactions;
    }

    @Override
    public <T> T adapt(Class<T> type)
    {
        if (type == Security.class)
            return type.cast(security);
        else if (type == Attributable.class)
            return type.cast(security);
        else if (type == Named.class)
            return type.cast(security);
        else if (type == InvestmentVehicle.class)
            return type.cast(security);
        else
            return null;
    }

    /* package */void addTransaction(Transaction t)
    {
        transactions.add(t);
    }

    /* package */void calculate(Client client, CurrencyConverter converter, ReportingPeriod period)
    {
        Collections.sort(transactions, new TransactionComparator());

        if (!transactions.isEmpty())
        {
            calculateMarketValue(converter);
            calculateIRR(converter);
            calculatePerformance(client, converter, period);
            calculateDelta(converter);
            calculateFifoCosts(converter);
            calculateDividends(converter);
        }
    }

    private void calculateMarketValue(CurrencyConverter converter)
    {
        MutableMoney mv = MutableMoney.of(converter.getTermCurrency());
        for (Transaction t : transactions)
            if (t instanceof DividendFinalTransaction)
                mv.add(t.getMonetaryAmount().with(converter.at(t.getDate())));
        this.marketValue = mv.toMoney();
    }

    private void calculateIRR(CurrencyConverter converter)
    {
        this.irr = Calculation.perform(IRRCalculation.class, converter, transactions).getIRR();
    }

    private void calculatePerformance(Client client, CurrencyConverter converter, ReportingPeriod period)
    {
        PerformanceIndex index = PerformanceIndex.forInvestment(client, converter, security, period,
                        new ArrayList<Exception>());
        this.twror = index.getFinalAccumulatedPercentage();
        this.drawdown = index.getDrawdown();
        this.volatility = index.getVolatility();
    }

    private void calculateDelta(CurrencyConverter converter)
    {
        this.delta = Calculation.perform(DeltaCalculation.class, converter, transactions).getDelta();
    }

    private void calculateFifoCosts(CurrencyConverter converter)
    {
        CostCalculation cost = Calculation.perform(CostCalculation.class, converter, transactions);
        this.fifoCost = cost.getFifoCost();
        this.sharesHeld = cost.getSharesHeld();

        Money netFifoCost = cost.getNetFifoCost();
        this.fifoCostPerSharesHeld = Money.of(netFifoCost.getCurrencyCode(),
                        Math.round(netFifoCost.getAmount() * Values.Share.factor() / (double) sharesHeld));

        this.fees = cost.getFees();
        this.taxes = cost.getTaxes();
    }

    private void calculateDividends(CurrencyConverter converter)
    {
        DividendCalculation dividends = Calculation.perform(DividendCalculation.class, converter, transactions);
        this.sumOfDividends = dividends.getSum();
        this.dividendEventCount = dividends.getNumOfEvents();
        this.lastDividendPayment = dividends.getLastDividendPayment();
        this.periodicity = dividends.getPeriodicity();
    }
}