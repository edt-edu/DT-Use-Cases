import { Component, ViewChild, ElementRef } from '@angular/core';
import { OnDestroy } from '@angular/core';
import { ComponentObserverManager } from '@umlp/commonj2ts';
import { ApexEnergyDifferenceVisualizationComponent, config } from 'components/ApexEnergyDifferenceVisualizationComponent';
import ApexCharts from 'apexcharts'
import { DoubleStreamObserver } from 'functionstructure4fenix/DoubleStreamObserver'
import { DoubleStream } from 'functionstructure4fenix/DoubleStream'
import { DoubleValue } from 'functionstructure4fenix/DoubleValue'
import { NotificationScope } from '@umlp/commonj2ts';

@Component({
  standalone: true,
  imports: [...config.imports],
  selector: config.selector,
  templateUrl: config.templateUrl,
  styles: config.styles,
  styleUrls: [...config.styleUrls]
})
export class ApexEnergyDifferenceVisualization extends ApexEnergyDifferenceVisualizationComponent implements OnDestroy {
  options: any;
  chart: ApexCharts;
  private registeredStream: DoubleStream;

  @ViewChild('diagramContainer', { static: true })
  public diagramContainer!: ElementRef;

  constructor() {
    super();
  }

  public override async init() {
    await super.init();

    this.options = {
      animations: {
        enabled: false,
        speed: 800,
        animateGradually: {
          enabled: false,
          delay: 150
        },
        dynamicAnimation: {
          enabled: false,
          speed: 350
        }
      },
      noData: {
        text: true ? "Loading..." : "No Data present in the graph!",
        align: 'center',
        verticalAlign: 'middle',
        offsetX: 0,
        offsetY: 0,
        style: {
          color: "#000000",
          fontSize: '14px',
          fontFamily: "Helvetica"
        }
      },
      chart: {
        id: 'energy-difference-datetime',
        type: 'area',
        height: 350,
        width: '100%',
        zoom: {
          autoScaleYaxis: true
        },
        events: {
          legendClick: function(chartContext, seriesIndex, opts) {
            this.options.series[seriesIndex].hidden = !this.options.series[seriesIndex].hidden;
          }.bind(this)
        }
      },
      series: [
        {
            name: "Energy difference",
            data: []
        }
      ],
      xaxis: {
        type: 'datetime',
        tickAmount: 6,
      },
      annotations: {
        yaxis: [],
        xaxis: []
      },
      dataLabels: {
        enabled: false
      },
      markers: {
        size: 0,
        style: 'hollow',
      },
      fill: {
        type: 'gradient',
        gradient: {
          shadeIntensity: 1,
          opacityFrom: 0.7,
          opacityTo: 0.9,
          stops: [0, 100]
        }
      },
    };

    this.chart = new ApexCharts(this.diagramContainer.nativeElement, this.options);
    this.chart.render();

    this.registerStream(this._energyDifference);
  }

  public override setEnergyDifference(val: DoubleStream): void {
    super.setEnergyDifference(val);
    this.registerStream(val);
  }

  private registerStream(stream: DoubleStream): void {
    if (!stream || !this.chart || this.registeredStream === stream) {
      return;
    }

    this.registeredStream = stream;
    this.loadExistingValues(stream);

    const thisRef = this;
    class ApexDoubleStreamObserver implements DoubleStreamObserver {
      notifyAddDoubleValue(doubleStream: DoubleStream, arg: number, indexInList: number) { }

      maybeNotifyAddDoubleValue(doubleStream: DoubleStream, arg: number, indexInList: number, scope: NotificationScope) {
        doubleStream.getDoubleValue(indexInList).then(res => {
          thisRef.addPoint(res);
        });
      }

      notifyRemoveDoubleValue(doubleStream: DoubleStream, arg: number, idx: number) { }

      maybeNotifyRemoveDoubleValue(doubleStream: DoubleStream, arg: number, idx: number, scope: NotificationScope) { }

      notifySetDoubleValue(doubleStream: DoubleStream, idx: number, oldValue: number, o: number) { }

      maybeNotifySetDoubleValue(doubleStream: DoubleStream, idx: number, oldValue: number, o: number, scope: NotificationScope) { }

      notifyAddObservation(functionStream: any, arg: number, indexInList: number) { }

      maybeNotifyAddObservation(functionStream: any, arg: number, indexInList: number, scope: NotificationScope) { }

      notifyRemoveObservation(functionStream: any, arg: number, idx: number) { }

      maybeNotifyRemoveObservation(functionStream: any, arg: number, idx: number, scope: NotificationScope) { }

      notifySetObservation(functionStream: any, idx: number, oldValue: number, o: number) { }

      maybeNotifySetObservation(functionStream: any, idx: number, oldValue: number, o: number, scope: NotificationScope) { }

      notifySetState(functionStream: any, oldValue: number, o: number) { }

      maybeNotifySetState(functionStream: any, oldValue: number, o: number, scope: NotificationScope) { }

      notifySetCategory(functionStream: any, oldValue: number, o: number) { }

      maybeNotifySetCategory(functionStream: any, oldValue: number, o: number, scope: NotificationScope) { }

      notifySetChannel(functionStream: any, oldValue: number, o: number) { }

      maybeNotifySetChannel(functionStream: any, oldValue: number, o: number, scope: NotificationScope) { }

      getScope(): NotificationScope { return NotificationScope.ALL; }

      notifyBuild(doubleStream: DoubleStream) { }

      maybeNotifyBuild(doubleStream: DoubleStream, scope: NotificationScope) { }
    }

    ApexDoubleStreamObserver["__interfaces"] = [
      "functionstructure4fenix.DoubleStreamObserver",
      "functionstructure4fenix.FunctionStreamObserver"
    ];

    stream.addObserver(new ApexDoubleStreamObserver());
  }

  private async loadExistingValues(stream: DoubleStream): Promise<void> {
    const values = stream.getDoubleValueList();
    const points: [number, number][] = [];
    const size = await values.size();
    for (let i = 0; i < size; i++) {
      const point = this.toPoint(await values.get(i));
      if (point !== null) {
        points.push(point);
      }
    }

    this.options.series[0].data = points;
    this.updateChart();
  }

  private addPoint(value: DoubleValue): void {
    const point = this.toPoint(value);
    if (point === null) {
      return;
    }
    this.options.series[0].data.push(point);
    this.updateChart();
  }

  private toPoint(value: DoubleValue): [number, number] | null {
    if (!value) {
      return null;
    }

    const timestamp = value.getTimestamp();
    const content = value.getContent();
    const time = new Date(timestamp).getTime();
    if (Number.isNaN(time) || content === undefined || content === null) {
      return null;
    }
    return [time, content];
  }

  private updateChart(): void {
    if (this.chart) {
      this.chart.updateSeries(this.options.series, false);
    }
  }

  public ngOnDestroy(): void {
    super.ngOnDestroy();
    ComponentObserverManager.removeObservers("components.ApexEnergyDifferenceVisualization" + this.uniqueIdentifier);
  }
}
